import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

/// Describes a single asset we have to fetch from HuggingFace.
class ModelAsset {
  final String url;
  final String relativePath; // path under the model root
  final String? sha256; // optional integrity check
  final bool optional; // a 404 on this asset is non-fatal
  const ModelAsset(
    this.url,
    this.relativePath, {
    this.sha256,
    this.optional = false,
  });
}

class ModelManifest {
  final String name;
  final String rootDirName;
  final List<ModelAsset> assets;
  const ModelManifest({
    required this.name,
    required this.rootDirName,
    required this.assets,
  });
}

/// Manages download + on-disk layout for on-device models.
///
/// v1 only ships the Kokoro-82M TTS bundle (~330 MB). The LLM Director was
/// removed in favour of a heuristic dialogue parser; if/when we add an
/// on-device LLM back, register a second [ModelManifest] here.
///
/// Models live under `getApplicationSupportDirectory()` so they survive app
/// upgrades but stay excluded from cloud backup (see data_extraction_rules).
class ModelService {
  ModelService._();
  static final ModelService instance = ModelService._();

  static const _kokoro = ModelManifest(
    name: 'kokoro-82m',
    rootDirName: 'kokoro-multi-lang-v1_0',
    assets: [
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/model.onnx',
        'model.onnx',
      ),
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/voices.bin',
        'voices.bin',
      ),
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/tokens.txt',
        'tokens.txt',
      ),
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/lexicon-us-en.txt',
        'lexicon-us-en.txt',
      ),
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/lexicon-gb-en.txt',
        'lexicon-gb-en.txt',
      ),
      // Optional: not all sherpa-onnx-kokoro releases publish voices.json. If
      // missing we fall back to a hard-coded sid map in TtsService.
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/voices.json',
        'voices.json',
        optional: true,
      ),
      // espeak-ng data is bundled as a tarball; extracted on first run.
      ModelAsset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/espeak-ng-data.tar.bz2',
        'espeak-ng-data.tar.bz2',
      ),
    ],
  );

  Future<Directory> _modelsRoot() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/models');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<Directory> kokoroDir() async {
    final root = await _modelsRoot();
    return Directory('${root.path}/${_kokoro.rootDirName}');
  }

  Future<bool> kokoroReady() => _manifestComplete(_kokoro);

  Future<bool> _manifestComplete(ModelManifest m) async {
    final root = await _modelsRoot();
    for (final a in m.assets) {
      if (a.optional) continue;
      final f = File('${root.path}/${m.rootDirName}/${a.relativePath}');
      if (!await f.exists()) return false;
      if (await f.length() < 1024) return false;
    }
    return true;
  }

  /// Streams `(received, total, label)` tuples while downloading. `total` may
  /// be -1 when the server omits Content-Length.
  Stream<(int received, int total, String label)> ensureAll() async* {
    yield* _ensure(_kokoro);
  }

  Stream<(int, int, String)> _ensure(ModelManifest m) async* {
    final root = await _modelsRoot();
    final modelDir = Directory('${root.path}/${m.rootDirName}');
    if (!await modelDir.exists()) await modelDir.create(recursive: true);

    for (final a in m.assets) {
      final dest = File('${modelDir.path}/${a.relativePath}');
      if (await dest.exists() && await dest.length() > 1024) continue;

      final label = '${m.name}:${a.relativePath}';
      final tmp = File('${dest.path}.part');
      if (await tmp.exists()) await tmp.delete();

      try {
        final req = http.Request('GET', Uri.parse(a.url));
        final resp = await http.Client().send(req);
        if (resp.statusCode != 200) {
          if (a.optional) {
            // Drain the body and skip silently.
            await resp.stream.drain();
            continue;
          }
          throw HttpException('HF download failed [${resp.statusCode}] ${a.url}');
        }
        final total = resp.contentLength ?? -1;
        var received = 0;
        final sink = tmp.openWrite();
        try {
          await for (final chunk in resp.stream) {
            sink.add(chunk);
            received += chunk.length;
            yield (received, total, label);
          }
        } finally {
          await sink.flush();
          await sink.close();
        }

        if (a.sha256 != null) {
          final actual = await _sha256(tmp);
          if (actual != a.sha256) {
            await tmp.delete();
            throw StateError('sha256 mismatch for $label: $actual');
          }
        }
        await tmp.rename(dest.path);

        // Auto-extract espeak-ng tarball.
        if (a.relativePath.endsWith('.tar.bz2')) {
          await _extractTarBz2(dest, modelDir);
        }
      } catch (e) {
        if (a.optional) {
          // ignore: avoid_print
          print('[ModelService] skipping optional asset $label: $e');
          continue;
        }
        rethrow;
      }
    }
  }

  Future<String> _sha256(File f) async {
    final digest = await sha256.bind(f.openRead()).first;
    return digest.toString();
  }

  Future<void> _extractTarBz2(File archive, Directory into) async {
    // Avoid pulling another package: shell out to busybox/tar if present.
    // Android API 26+ ships a usable `tar` via toybox.
    final result = await Process.run(
      'tar',
      ['-xjf', archive.path, '-C', into.path],
      runInShell: true,
    );
    if (result.exitCode != 0) {
      // Non-fatal: keep the archive so the next run can retry.
      // ignore: avoid_print
      print('[ModelService] tar extract failed: ${result.stderr}');
    }
  }
}
