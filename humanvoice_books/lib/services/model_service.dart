import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

/// Describes a single asset we have to fetch from HuggingFace.
class _Asset {
  final String url;
  final String relativePath; // path under the model root
  final int? expectedBytes; // for progress; null = unknown
  final String? sha256; // optional integrity check
  const _Asset(this.url, this.relativePath, {this.expectedBytes, this.sha256});
}

class ModelManifest {
  final String name;
  final String rootDirName;
  final List<_Asset> assets;
  const ModelManifest({
    required this.name,
    required this.rootDirName,
    required this.assets,
  });
}

/// Manages download + on-disk layout for the two on-device models:
///   1. Kokoro-82M (TTS) — ONNX, Opset 15
///   2. Qwen2.5-1.5B-Instruct Q4_K_M (Director LLM) — sherpa-onnx GGUF wrapper
///
/// Models live under `getApplicationSupportDirectory()` so they survive
/// app upgrades but are excluded from cloud backup (see manifest rules).
class ModelService {
  ModelService._();
  static final ModelService instance = ModelService._();

  // ---- Kokoro-82M (Opset 15, sherpa-onnx packaging) ----
  static const _kokoro = ModelManifest(
    name: 'kokoro-82m',
    rootDirName: 'kokoro-multi-lang-v1_0',
    assets: [
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/model.onnx',
        'model.onnx',
      ),
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/voices.bin',
        'voices.bin',
      ),
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/tokens.txt',
        'tokens.txt',
      ),
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/lexicon-us-en.txt',
        'lexicon-us-en.txt',
      ),
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/lexicon-gb-en.txt',
        'lexicon-gb-en.txt',
      ),
      // espeak-ng data is bundled as a tarball; extracted on first run.
      _Asset(
        'https://huggingface.co/csukuangfj/sherpa-onnx-kokoro-multi-lang-v1_0/resolve/main/espeak-ng-data.tar.bz2',
        'espeak-ng-data.tar.bz2',
      ),
    ],
  );

  // ---- Qwen2.5-1.5B-Instruct (Q4_K_M GGUF, served via sherpa-onnx OfflineLlm) ----
  static const _qwen = ModelManifest(
    name: 'qwen2.5-1.5b-instruct',
    rootDirName: 'qwen2_5-1_5b-instruct',
    assets: [
      _Asset(
        'https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf',
        'qwen2.5-1.5b-instruct-q4_k_m.gguf',
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

  Future<Directory> qwenDir() async {
    final root = await _modelsRoot();
    return Directory('${root.path}/${_qwen.rootDirName}');
  }

  Future<bool> kokoroReady() => _manifestComplete(_kokoro);
  Future<bool> qwenReady() => _manifestComplete(_qwen);

  Future<bool> _manifestComplete(ModelManifest m) async {
    final root = await _modelsRoot();
    for (final a in m.assets) {
      final f = File('${root.path}/${m.rootDirName}/${a.relativePath}');
      if (!await f.exists()) return false;
      if (await f.length() < 1024) return false; // failed/partial
    }
    return true;
  }

  /// Streams (received, total) tuples while downloading. `total` may be -1
  /// when the server omits Content-Length.
  Stream<(int received, int total, String label)> ensureAll() async* {
    yield* _ensure(_kokoro);
    yield* _ensure(_qwen);
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

      final req = http.Request('GET', Uri.parse(a.url));
      final resp = await http.Client().send(req);
      if (resp.statusCode != 200) {
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
    }
  }

  Future<String> _sha256(File f) async {
    final digest = await sha256.bind(f.openRead()).first;
    return digest.toString();
  }

  Future<void> _extractTarBz2(File archive, Directory into) async {
    // Avoid pulling another package: shell out to busybox/tar if present,
    // otherwise leave the archive — sherpa-onnx will read it lazily on some
    // builds, or the user can re-run extraction. On API 26+ Android ships
    // a usable `tar` via toybox.
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
