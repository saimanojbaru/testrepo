import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:archive/archive_io.dart';
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
  final String modelFile; // file sherpa_onnx loads
  final List<String> requiredFiles; // verified post-extract before stamping ready
  final List<String> requiredDirs; // dirs that must exist post-extract
  final List<ModelAsset> assets;
  final int version; // bump to invalidate any prior partial state
  const ModelManifest({
    required this.name,
    required this.rootDirName,
    required this.modelFile,
    required this.requiredFiles,
    required this.requiredDirs,
    required this.assets,
    this.version = 1,
  });
}

/// Manages download + on-disk layout for on-device models.
///
/// Atomicity model:
///   - "<rootDir>/.ready_v$version" is the ONLY readiness signal.
///   - It's written exactly once, after every required file/dir is verified.
///   - On launch, if the rootDir exists but the marker doesn't, we treat the
///     extraction as botched (e.g. process killed mid-extract by Android LMK)
///     and wipe the rootDir before redoing the work.
///   - This protects against the "false sentinel" trap where a partial
///     extract leaves model.int8.onnx on disk but tokens.txt missing.
class ModelService {
  ModelService._();
  static final ModelService instance = ModelService._();

  static const _kokoro = ModelManifest(
    name: 'kokoro-int8-82m',
    rootDirName: 'kokoro-int8-multi-lang-v1_0',
    modelFile: 'model.int8.onnx',
    requiredFiles: [
      'model.int8.onnx',
      'tokens.txt',
      'voices.bin',
      'lexicon-us-en.txt',
      'lexicon-gb-en.txt',
    ],
    requiredDirs: ['espeak-ng-data'],
    // Bumped from v1 -> v2 alongside the streaming-extract rewrite so any
    // device carrying a corrupted partial extract from before is auto-wiped.
    version: 2,
    assets: [
      ModelAsset(
        // Single bundled archive. FP32 fallback (also includes espeak-ng-data):
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2
        'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2',
        '_archives/kokoro-int8-multi-lang-v1_0.tar.bz2',
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
  String get kokoroModelFile => _kokoro.modelFile;

  Future<bool> kokoroReady() => _manifestReady(_kokoro);

  Future<bool> _manifestReady(ModelManifest m) async {
    final root = await _modelsRoot();
    final marker = File('${root.path}/${m.rootDirName}/.ready_v${m.version}');
    return marker.exists();
  }

  Future<void> _wipeIfPartial(ModelManifest m) async {
    final root = await _modelsRoot();
    final dir = Directory('${root.path}/${m.rootDirName}');
    if (!await dir.exists()) return;
    if (await _manifestReady(m)) return; // valid existing extract
    // ignore: avoid_print
    print('[ModelService] partial extract detected at ${dir.path}; wiping');
    try {
      await dir.delete(recursive: true);
    } catch (e) {
      // ignore: avoid_print
      print('[ModelService] wipe failed: $e (continuing; extract will overwrite)');
    }
  }

  Future<void> _markReady(ModelManifest m) async {
    final root = await _modelsRoot();
    final modelDir = Directory('${root.path}/${m.rootDirName}');

    // Verify every required file is present and non-empty BEFORE stamping.
    for (final relPath in m.requiredFiles) {
      final f = File('${modelDir.path}/$relPath');
      if (!await f.exists()) {
        throw StateError('post-extract verify: missing $relPath');
      }
      if (await f.length() < 100) {
        throw StateError('post-extract verify: $relPath is empty');
      }
    }
    for (final relDir in m.requiredDirs) {
      final d = Directory('${modelDir.path}/$relDir');
      if (!await d.exists()) {
        throw StateError('post-extract verify: missing dir $relDir');
      }
    }

    final marker = File('${modelDir.path}/.ready_v${m.version}');
    await marker.writeAsString(DateTime.now().toIso8601String());
    // ignore: avoid_print
    print('[ModelService] sentinel written: ${marker.path}');
  }

  /// Streams `(received, total, label)` tuples while downloading.
  Stream<(int received, int total, String label)> ensureAll() async* {
    yield* _ensure(_kokoro);
  }

  Stream<(int, int, String)> _ensure(ModelManifest m) async* {
    await _wipeIfPartial(m);
    if (await _manifestReady(m)) {
      yield (1, 1, '${m.name}: already on device');
      return;
    }

    final root = await _modelsRoot();
    final modelDir = Directory('${root.path}/${m.rootDirName}');
    if (!await modelDir.exists()) await modelDir.create(recursive: true);

    for (final a in m.assets) {
      final dest = File('${root.path}/${a.relativePath}');
      if (!await dest.parent.exists()) {
        await dest.parent.create(recursive: true);
      }
      if (!(await dest.exists() && await dest.length() > 1024)) {
        yield* _download(a, dest, '${m.name}:${a.relativePath}');
      }

      if (a.relativePath.endsWith('.tar.bz2')) {
        yield (0, 1, '${m.name}: extracting…');
        await _extractTarBz2(dest, modelDir, a.stripComponents);
        // Reclaim ~80 MB once extraction succeeded; re-download on next
        // ensureAll if anything goes wrong (the marker won't be there).
        try {
          await dest.delete();
        } catch (_) {}
      }
    }

    // Will throw with a precise message if any required file is missing.
    await _markReady(m);
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

  /// Two-stage streaming extract. Runs inside a worker isolate so the heavy
  /// allocations are isolated from the UI heap and the OS LMK gets a clean
  /// target if memory pressure spikes (the marker file is the only thing
  /// that says "we're done"; if the worker dies, nothing rolls forward).
  Future<void> _extractTarBz2(
    File archive,
    Directory into,
    int stripComponents,
  ) async {
    final archivePath = archive.path;
    final intoPath = into.path;
    await Isolate.run(
      () => _streamingExtract(archivePath, intoPath, stripComponents),
    );
  }

  static Future<void> _streamingExtract(
    String archivePath,
    String intoPath,
    int strip,
  ) async {
    // Stage 1: bz2 -> sibling .tar on disk.
    // Avoids the 300 MB peak heap that BZip2Decoder.decodeBytes would need.
    final tarPath = '$archivePath.tar';
    final inputBz = InputFileStream(archivePath);
    final outputTar = OutputFileStream(tarPath);
    try {
      BZip2Decoder().decodeStream(inputBz, outputTar);
    } finally {
      inputBz.close();
      outputTar.close();
    }

    // Stage 2: stream-untar from disk into the target dir, one file at a time.
    final tarStream = InputFileStream(tarPath);
    var written = 0;
    try {
      final ar = TarDecoder().decodeBuffer(tarStream);
      for (final entry in ar) {
        if (!entry.isFile) continue;
        final parts = entry.name.split('/');
        if (parts.length <= strip) continue;
        final rel = parts.sublist(strip).join('/');
        if (rel.isEmpty) continue;

        final out = File('$intoPath/$rel');
        await out.parent.create(recursive: true);
        await out.writeAsBytes(entry.content as List<int>, flush: true);
        written++;
      }
    } finally {
      tarStream.close();
    }
    // ignore: avoid_print
    print('[ModelService] streamed $written files into $intoPath');

    // Stage 3: drop the intermediate uncompressed tar (~300 MB).
    try {
      await File(tarPath).delete();
    } catch (_) {}
  }
}
