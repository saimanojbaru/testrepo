import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

/// Describes a single asset we have to fetch.
class ModelAsset {
  final String url;
  final String relativePath; // path under the models root directory
  final String? sha256; // optional integrity check
  final bool optional; // a 404 on this asset is non-fatal
  final int stripComponents; // tar --strip-components when auto-extracting
  const ModelAsset(
    this.url,
    this.relativePath, {
    this.sha256,
    this.optional = false,
    this.stripComponents = 0,
  });
}

class ModelManifest {
  final String name;
  final String rootDirName; // the on-disk dir relative to /models
  final String sentinelFile; // file used to decide if model is ready
  final List<ModelAsset> assets;
  const ModelManifest({
    required this.name,
    required this.rootDirName,
    required this.sentinelFile,
    required this.assets,
  });
}

/// Manages download + on-disk layout for on-device models.
///
/// v1 ships the Kokoro-82M INT8 bundle from sherpa-onnx's k2-fsa GitHub
/// release. One tarball (~80 MB compressed, ~300 MB extracted) contains
/// everything Kokoro needs: model.int8.onnx, tokens.txt, voices.bin,
/// lexicons, and espeak-ng-data/. We deliberately avoid Hugging Face here
/// because the kokoro-multi-lang-v1_0 HF repo doesn't ship espeak-ng-data
/// as a tarball — it stores it as a flat directory of ~200 small files,
/// which would mean ~200 sequential HTTP requests on first launch.
///
/// Models live under `getApplicationSupportDirectory()` so they survive
/// app upgrades but stay excluded from cloud backup
/// (see android/.../data_extraction_rules.xml).
class ModelService {
  ModelService._();
  static final ModelService instance = ModelService._();

  static const _kokoro = ModelManifest(
    name: 'kokoro-int8-82m',
    rootDirName: 'kokoro-int8-multi-lang-v1_0',
    sentinelFile: 'model.int8.onnx',
    assets: [
      ModelAsset(
        // Single bundled archive. If this URL ever 404s (release rename),
        // the FP32 fallback is:
        //   https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2
        // (~330 MB compressed, also includes espeak-ng-data/).
        'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2',
        '_archives/kokoro-int8-multi-lang-v1_0.tar.bz2',
        // Tarball wraps everything in a top-level dir matching its name; we
        // strip that and extract directly into rootDirName.
        stripComponents: 1,
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

  /// Filename of the Kokoro ONNX model, relative to [kokoroDir].
  String get kokoroModelFile => _kokoro.sentinelFile;

  Future<bool> kokoroReady() => _manifestReady(_kokoro);

  Future<bool> _manifestReady(ModelManifest m) async {
    final root = await _modelsRoot();
    final sentinel = File('${root.path}/${m.rootDirName}/${m.sentinelFile}');
    if (!await sentinel.exists()) return false;
    return await sentinel.length() > 1024 * 1024; // model is many MB
  }

  /// Streams `(received, total, label)` tuples while downloading.
  /// `total` may be -1 when the server omits Content-Length.
  Stream<(int received, int total, String label)> ensureAll() async* {
    yield* _ensure(_kokoro);
  }

  Stream<(int, int, String)> _ensure(ModelManifest m) async* {
    final root = await _modelsRoot();
    final modelDir = Directory('${root.path}/${m.rootDirName}');
    if (!await modelDir.exists()) await modelDir.create(recursive: true);

    // Short-circuit if already extracted from a previous run.
    if (await _manifestReady(m)) {
      yield (1, 1, '${m.name}: already on device');
      return;
    }

    for (final a in m.assets) {
      final dest = File('${root.path}/${a.relativePath}');
      if (!await dest.parent.exists()) {
        await dest.parent.create(recursive: true);
      }
      if (await dest.exists() && await dest.length() > 1024) {
        // Already downloaded, skip to extract step below.
      } else {
        yield* _download(a, dest, '${m.name}:${a.relativePath}');
      }

      if (a.relativePath.endsWith('.tar.bz2')) {
        await _extractTarBz2(dest, modelDir, a.stripComponents);
        // Reclaim ~80 MB once extraction succeeds.
        if (await _manifestReady(m)) {
          try {
            await dest.delete();
          } catch (_) {}
        }
      }
    }

    if (!await _manifestReady(m)) {
      throw StateError(
        'Model still not ready after download/extract for ${m.name}. '
        'Check the URL and that the device has sufficient free space.',
      );
    }
  }

  Stream<(int, int, String)> _download(
    ModelAsset a,
    File dest,
    String label,
  ) async* {
    final tmp = File('${dest.path}.part');
    if (await tmp.exists()) await tmp.delete();

    try {
      final req = http.Request('GET', Uri.parse(a.url))
        ..followRedirects = true;
      final resp = await http.Client().send(req);
      if (resp.statusCode != 200) {
        if (a.optional) {
          await resp.stream.drain();
          return;
        }
        throw HttpException('Download failed [${resp.statusCode}] ${a.url}');
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
    } catch (e) {
      if (a.optional) {
        // ignore: avoid_print
        print('[ModelService] skipping optional asset $label: $e');
        return;
      }
      rethrow;
    }
  }

  Future<String> _sha256(File f) async {
    final digest = await sha256.bind(f.openRead()).first;
    return digest.toString();
  }

  Future<void> _extractTarBz2(
    File archive,
    Directory into,
    int stripComponents,
  ) async {
    // Android API 26+ ships a usable `tar` via toybox. On the rare device
    // where it isn't on PATH, the manifest still has the .tar.bz2 around
    // for a manual fix.
    final args = <String>[
      '-xjf',
      archive.path,
      '-C',
      into.path,
      if (stripComponents > 0) '--strip-components=$stripComponents',
    ];
    final result = await Process.run('tar', args, runInShell: true);
    if (result.exitCode != 0) {
      // ignore: avoid_print
      print('[ModelService] tar extract failed: ${result.stderr}');
    }
  }
}
