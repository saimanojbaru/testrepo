import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';
import 'package:sherpa_onnx/sherpa_onnx.dart' as so;

import '../models/segment.dart';
import 'model_service.dart';

/// Static voice mapping from Kokoro speaker names to integer IDs.
/// (Kokoro v1.0 ships ~54 voices in a single voices.bin matrix.)
const Map<String, int> kKokoroVoiceIds = {
  'af_bella': 0,
  'af_nicole': 4,
  'af_sarah': 5,
  'am_adam': 14,
  'am_michael': 19,
};

class TtsRenderResult {
  final File wavFile;
  final Duration duration;
  final int sampleRate;
  TtsRenderResult(this.wavFile, this.duration, this.sampleRate);
}

/// Enactor: turns a list of [Segment]s into one WAV file per chapter.
///
/// Background pipelining:
///   - while Chapter 1 plays, the [renderChapterInBackground] call kicks
///     off a fresh Isolate that pre-renders Chapter 2 into the cache.
class TtsService {
  TtsService._();
  static final TtsService instance = TtsService._();

  so.OfflineTts? _tts;

  Future<void> init() async {
    if (_tts != null) return;
    final dir = await ModelService.instance.kokoroDir();
    final cfg = so.OfflineTtsConfig(
      model: so.OfflineTtsModelConfig(
        kokoro: so.OfflineTtsKokoroModelConfig(
          model: '${dir.path}/model.onnx',
          voices: '${dir.path}/voices.bin',
          tokens: '${dir.path}/tokens.txt',
          dataDir: '${dir.path}/espeak-ng-data',
          lengthScale: 1.0,
        ),
        numThreads: 2,
        provider: 'cpu',
        debug: false,
      ),
      ruleFsts: '',
      ruleFars: '',
      maxNumSentences: 4,
    );
    _tts = so.OfflineTts(config: cfg);
  }

  void dispose() {
    _tts?.free();
    _tts = null;
  }

  /// Render a chapter into a single WAV file. Each segment is generated
  /// independently then concatenated in-memory before writing — this lets
  /// us swap voices/emotions across the chapter.
  Future<TtsRenderResult> renderChapter({
    required Chapter chapter,
    required Directory outputDir,
  }) async {
    await init();
    final tts = _tts!;
    if (!await outputDir.exists()) await outputDir.create(recursive: true);

    final pcmChunks = <Float32List>[];
    int sr = tts.sampleRate;

    for (final seg in chapter.segments) {
      final voiceId = kKokoroVoiceIds[seg.voiceId] ?? 0;
      final speed = _emotionToSpeed(seg.emotion, seg.speed);
      final result = tts.generate(
        text: seg.text,
        sid: voiceId,
        speed: speed,
      );
      pcmChunks.add(result.samples);
      sr = result.sampleRate;
      // Quarter-second beat between segments to breathe.
      pcmChunks.add(Float32List(sr ~/ 4));
    }

    final merged = _concat(pcmChunks);
    final wav = File('${outputDir.path}/chapter_${chapter.index.toString().padLeft(4, '0')}.wav');
    await _writeWav(wav, merged, sr);
    final dur = Duration(milliseconds: (merged.length * 1000) ~/ sr);
    return TtsRenderResult(wav, dur, sr);
  }

  /// Spawns an isolate that renders [chapter] in the background while the
  /// caller plays a different chapter. Returns the future WAV path.
  Future<String> renderChapterInBackground(Chapter chapter) async {
    final outDir = await _chapterCacheDir();
    final kokoro = await ModelService.instance.kokoroDir();
    final rx = ReceivePort();
    await Isolate.spawn<_BgRequest>(
      _bgEntry,
      _BgRequest(rx.sendPort, chapter, outDir.path, kokoro.path),
    );
    final result = await rx.first;
    rx.close();
    if (result is String) return result;
    throw StateError('Background render failed: $result');
  }

  Future<Directory> _chapterCacheDir() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/audiobook_cache');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
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

  // 16-bit PCM WAV writer. Kept self-contained — sherpa-onnx returns float32
  // PCM and we want a real on-disk WAV that ffmpeg can mux.
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
    writeU16(16); // bits per sample
    writeStr('data');
    writeU32(dataSize);

    // float32 -> int16 with clipping
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

  // ===== background isolate =====

  static Future<void> _bgEntry(_BgRequest req) async {
    try {
      // Re-init TtsService inside this isolate (sherpa-onnx state is per-iso).
      final cfg = so.OfflineTtsConfig(
        model: so.OfflineTtsModelConfig(
          kokoro: so.OfflineTtsKokoroModelConfig(
            model: '${req.kokoroDir}/model.onnx',
            voices: '${req.kokoroDir}/voices.bin',
            tokens: '${req.kokoroDir}/tokens.txt',
            dataDir: '${req.kokoroDir}/espeak-ng-data',
            lengthScale: 1.0,
          ),
          numThreads: 2,
          provider: 'cpu',
          debug: false,
        ),
        ruleFsts: '',
        ruleFars: '',
        maxNumSentences: 4,
      );
      final tts = so.OfflineTts(config: cfg);

      final pcm = <Float32List>[];
      int sr = tts.sampleRate;
      for (final s in req.chapter.segments) {
        final voiceId = kKokoroVoiceIds[s.voiceId] ?? 0;
        final r = tts.generate(
          text: s.text,
          sid: voiceId,
          speed: _emotionToSpeed(s.emotion, s.speed),
        );
        pcm.add(r.samples);
        sr = r.sampleRate;
        pcm.add(Float32List(sr ~/ 4));
      }

      final merged = _concat(pcm);
      final wav = File('${req.outDir}/chapter_${req.chapter.index.toString().padLeft(4, '0')}.wav');
      await _writeWav(wav, merged, sr);
      tts.free();
      req.reply.send(wav.path);
    } catch (e) {
      req.reply.send('error:$e');
    }
  }
}

class _BgRequest {
  final SendPort reply;
  final Chapter chapter;
  final String outDir;
  final String kokoroDir;
  _BgRequest(this.reply, this.chapter, this.outDir, this.kokoroDir);
}
