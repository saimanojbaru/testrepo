import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';
import 'package:sherpa_onnx/sherpa_onnx.dart' as so;

import '../models/segment.dart';
import 'model_service.dart';

class TtsRenderResult {
  final File wavFile;
  final Duration duration;
  final int sampleRate;
  TtsRenderResult(this.wavFile, this.duration, this.sampleRate);
}

/// Enactor: turns a list of [Segment]s into one WAV file per chapter.
///
/// Background pipelining: while chapter N plays, [renderChapterInBackground]
/// spawns a fresh Isolate that renders chapter N+1 into the same cache
/// directory. The next foreground render then picks up the prebaked file.
class TtsService {
  TtsService._();
  static final TtsService instance = TtsService._();

  static bool _bindingsReady = false;
  so.OfflineTts? _tts;
  Map<String, int> _voiceMap = const {'af_bella': 0};

  /// Speaker name → integer sid. Populated from `voices.json` once the model
  /// is downloaded; falls back to a defensive map containing only `af_bella`.
  Map<String, int> get voiceMap => _voiceMap;

  Future<void> init() async {
    if (_tts != null) return;
    if (!_bindingsReady) {
      so.initBindings();
      _bindingsReady = true;
    }
    final dir = await ModelService.instance.kokoroDir();
    final modelFile = ModelService.instance.kokoroModelFile;
    _voiceMap = await _loadVoiceMap(dir);

    // Pre-flight: native sherpa-onnx will SIGSEGV in the background isolate
    // (silent hang via Future never completing) if any of these are missing.
    // Surface a Dart exception instead so the BLoC error banner shows the bug.
    await _assertExists('${dir.path}/$modelFile', 'kokoro model');
    await _assertExists('${dir.path}/voices.bin', 'kokoro voices');
    await _assertExists('${dir.path}/tokens.txt', 'kokoro tokens');
    await _assertExists('${dir.path}/lexicon-us-en.txt', 'kokoro lexicon');
    await _assertDir('${dir.path}/espeak-ng-data', 'espeak-ng data');

    // ignore: avoid_print
    print('[TtsService] init kokoro=${dir.path}/$modelFile voices=${_voiceMap.length}');

    final cfg = so.OfflineTtsConfig(
      model: so.OfflineTtsModelConfig(
        kokoro: so.OfflineTtsKokoroModelConfig(
          model: '${dir.path}/$modelFile',
          voices: '${dir.path}/voices.bin',
          tokens: '${dir.path}/tokens.txt',
          dataDir: '${dir.path}/espeak-ng-data',
          lexicon: '${dir.path}/lexicon-us-en.txt',
          lengthScale: 1.0,
        ),
        numThreads: 2,
        debug: false,
        provider: 'cpu',
      ),
      // Note: the upstream plugin spells this 'maxNumSenetences' (typo).
      maxNumSenetences: 4,
    );
    try {
      _tts = so.OfflineTts(cfg);
    } catch (e, st) {
      // ignore: avoid_print
      print('[TtsService] OfflineTts construction failed: $e\n$st');
      rethrow;
    }
    // ignore: avoid_print
    print('[TtsService] OfflineTts ready, sampleRate=${_tts!.sampleRate}');
  }

  static Future<void> _assertExists(String path, String label) async {
    final f = File(path);
    if (!await f.exists()) {
      throw StateError('Missing $label at $path');
    }
    final len = await f.length();
    if (len < 1024) {
      throw StateError('$label at $path is suspiciously small ($len bytes)');
    }
  }

  static Future<void> _assertDir(String path, String label) async {
    final d = Directory(path);
    if (!await d.exists()) {
      throw StateError('Missing $label directory at $path');
    }
  }

  void dispose() {
    _tts?.free();
    _tts = null;
  }

  /// Render a chapter into a single WAV. Each segment is generated independently
  /// so we can swap voices/emotions across the chapter, then concatenated with
  /// a quarter-second silence between segments to breathe.
  Future<TtsRenderResult> renderChapter({
    required Chapter chapter,
    required Directory outputDir,
  }) async {
    await init();
    final tts = _tts!;
    if (!await outputDir.exists()) await outputDir.create(recursive: true);

    final pcmChunks = <Float32List>[];
    int sr = tts.sampleRate;
    final defaultSid = _voiceMap['af_bella'] ?? 0;

    for (final seg in chapter.segments) {
      final sid = _voiceMap[seg.voiceId] ?? defaultSid;
      final speed = _emotionToSpeed(seg.emotion, seg.speed);
      final audio = tts.generate(text: seg.text, sid: sid, speed: speed);
      pcmChunks.add(audio.samples);
      sr = audio.sampleRate;
      pcmChunks.add(Float32List(sr ~/ 4));
    }

    final merged = _concat(pcmChunks);
    final wav = File(
      '${outputDir.path}/chapter_${chapter.index.toString().padLeft(4, '0')}.wav',
    );
    await _writeWav(wav, merged, sr);
    final dur = Duration(milliseconds: (merged.length * 1000) ~/ sr);
    return TtsRenderResult(wav, dur, sr);
  }

  /// Spawns an isolate that renders [chapter] to disk. Returns the absolute
  /// path of the resulting WAV. sherpa_onnx is FFI, so the isolate just
  /// needs its own [so.initBindings] call before constructing the engine.
  Future<String> renderChapterInBackground(Chapter chapter) async {
    await init(); // ensure voiceMap is loaded
    final outDir = await chapterCacheDir();
    final kokoro = await ModelService.instance.kokoroDir();
    final modelFile = ModelService.instance.kokoroModelFile;
    final rx = ReceivePort();
    await Isolate.spawn<_BgRequest>(
      _bgEntry,
      _BgRequest(rx.sendPort, chapter, outDir.path, kokoro.path, modelFile, _voiceMap),
    );
    final result = await rx.first;
    rx.close();
    if (result is String && !result.startsWith('error:')) return result;
    throw StateError('Background render failed: $result');
  }

  Future<Directory> chapterCacheDir() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/audiobook_cache');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  // -------- helpers --------

  static Future<Map<String, int>> _loadVoiceMap(Directory modelDir) async {
    final f = File('${modelDir.path}/voices.json');
    if (!await f.exists()) return const {'af_bella': 0};
    try {
      final raw = jsonDecode(await f.readAsString());
      // Two known layouts: {"af_bella": 2, ...} or {"speakers": ["af_alloy", "af_aoede", ...]}.
      if (raw is Map) {
        final m = <String, int>{};
        raw.forEach((k, v) {
          if (k is String && v is num) m[k] = v.toInt();
        });
        if (m.isNotEmpty) return m;
      }
      if (raw is Map && raw['speakers'] is List) {
        final list = (raw['speakers'] as List).cast<Object?>();
        return {
          for (var i = 0; i < list.length; i++)
            if (list[i] is String) list[i] as String: i,
        };
      }
    } catch (_) {/* fall through to default */}
    return const {'af_bella': 0};
  }

  static double _emotionToSpeed(String emotion, double base) {
    switch (emotion) {
      case 'whisper':
        return base * 0.92;
      case 'shout':
        return base * 1.05;
      case 'sad':
        return base * 0.95;
      case 'excited':
        return base * 1.08;
      default:
        return base;
    }
  }

  static Float32List _concat(List<Float32List> chunks) {
    final total = chunks.fold<int>(0, (a, b) => a + b.length);
    final out = Float32List(total);
    var off = 0;
    for (final c in chunks) {
      out.setAll(off, c);
      off += c.length;
    }
    return out;
  }

  static Future<void> _writeWav(File f, Float32List samples, int sampleRate) async {
    final byteRate = sampleRate * 2;
    final dataSize = samples.length * 2;
    final buf = BytesBuilder();

    void writeStr(String s) => buf.add(s.codeUnits);
    void writeU32(int v) => buf.add([
          v & 0xff,
          (v >> 8) & 0xff,
          (v >> 16) & 0xff,
          (v >> 24) & 0xff,
        ]);
    void writeU16(int v) => buf.add([v & 0xff, (v >> 8) & 0xff]);

    writeStr('RIFF');
    writeU32(36 + dataSize);
    writeStr('WAVE');
    writeStr('fmt ');
    writeU32(16);
    writeU16(1); // PCM
    writeU16(1); // mono
    writeU32(sampleRate);
    writeU32(byteRate);
    writeU16(2); // block align
    writeU16(16); // bits/sample
    writeStr('data');
    writeU32(dataSize);

    final pcm = Int16List(samples.length);
    for (var i = 0; i < samples.length; i++) {
      var v = (samples[i] * 32767).round();
      if (v > 32767) v = 32767;
      if (v < -32768) v = -32768;
      pcm[i] = v;
    }
    buf.add(pcm.buffer.asUint8List());

    await f.writeAsBytes(buf.takeBytes(), flush: true);
  }

  // -------- background isolate entry --------

  static Future<void> _bgEntry(_BgRequest req) async {
    try {
      so.initBindings();
      final cfg = so.OfflineTtsConfig(
        model: so.OfflineTtsModelConfig(
          kokoro: so.OfflineTtsKokoroModelConfig(
            model: '${req.kokoroDir}/${req.modelFile}',
            voices: '${req.kokoroDir}/voices.bin',
            tokens: '${req.kokoroDir}/tokens.txt',
            dataDir: '${req.kokoroDir}/espeak-ng-data',
            lexicon: '${req.kokoroDir}/lexicon-us-en.txt',
            lengthScale: 1.0,
          ),
          numThreads: 2,
          debug: false,
          provider: 'cpu',
        ),
        maxNumSenetences: 4,
      );
      final tts = so.OfflineTts(cfg);

      final pcm = <Float32List>[];
      int sr = tts.sampleRate;
      final defaultSid = req.voiceMap['af_bella'] ?? 0;
      for (final s in req.chapter.segments) {
        final sid = req.voiceMap[s.voiceId] ?? defaultSid;
        final r = tts.generate(
          text: s.text,
          sid: sid,
          speed: _emotionToSpeed(s.emotion, s.speed),
        );
        pcm.add(r.samples);
        sr = r.sampleRate;
        pcm.add(Float32List(sr ~/ 4));
      }

      final merged = _concat(pcm);
      final wav = File(
        '${req.outDir}/chapter_${req.chapter.index.toString().padLeft(4, '0')}.wav',
      );
      await _writeWav(wav, merged, sr);
      tts.free();
      req.reply.send(wav.path);
    } catch (e, st) {
      req.reply.send('error:$e\n$st');
    }
  }
}

class _BgRequest {
  final SendPort reply;
  final Chapter chapter;
  final String outDir;
  final String kokoroDir;
  final String modelFile;
  final Map<String, int> voiceMap;
  _BgRequest(
    this.reply,
    this.chapter,
    this.outDir,
    this.kokoroDir,
    this.modelFile,
    this.voiceMap,
  );
}
