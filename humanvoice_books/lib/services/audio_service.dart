import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';

import '../models/segment.dart';
import 'book_analyzer.dart';
import 'tts_service.dart';

enum AudioStage { idle, preparing, playing, paused, error }

class AudioState {
  final AudioStage stage;
  final String? chapterTitle;
  final Duration position;
  final Duration duration;
  final String? error;
  const AudioState({
    this.stage = AudioStage.idle,
    this.chapterTitle,
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.error,
  });

  AudioState copyWith({
    AudioStage? stage,
    String? chapterTitle,
    Duration? position,
    Duration? duration,
    String? error,
  }) =>
      AudioState(
        stage: stage ?? this.stage,
        chapterTitle: chapterTitle ?? this.chapterTitle,
        position: position ?? this.position,
        duration: duration ?? this.duration,
        error: error ?? this.error,
      );
}

/// Bridges [TtsService] (renders WAV) and [just_audio.AudioPlayer] (plays it).
///
/// Designed so the reader UI can call `playRawText(...)` and get a streamable
/// state without ever touching the locked TTS backend. Exposes [state] as a
/// [ValueNotifier] so any widget can `ValueListenableBuilder` it.
class AudioService {
  AudioService._();
  static final AudioService instance = AudioService._();

  final ValueNotifier<AudioState> state = ValueNotifier(const AudioState());
  final AudioPlayer _player = AudioPlayer();

  StreamSubscription<Duration>? _posSub;
  StreamSubscription<Duration?>? _durSub;
  StreamSubscription<PlayerState>? _stateSub;

  bool _wired = false;
  void _wirePlayerListeners() {
    if (_wired) return;
    _wired = true;
    _posSub = _player.positionStream.listen((p) {
      state.value = state.value.copyWith(position: p);
    });
    _durSub = _player.durationStream.listen((d) {
      if (d != null) state.value = state.value.copyWith(duration: d);
    });
    _stateSub = _player.playerStateStream.listen((ps) {
      if (ps.processingState == ProcessingState.completed) {
        state.value = state.value.copyWith(
          stage: AudioStage.idle,
          position: Duration.zero,
        );
        return;
      }
      if (ps.playing) {
        state.value = state.value.copyWith(stage: AudioStage.playing);
      } else if (state.value.stage == AudioStage.playing) {
        state.value = state.value.copyWith(stage: AudioStage.paused);
      }
    });
  }

  /// Render [text] (typically a chapter or paragraph) via TtsService and
  /// play it. The render itself is synchronous in the locked backend, so we
  /// surface a `preparing` stage while it runs and `playing` when audio
  /// actually begins.
  Future<void> playRawText({
    required String text,
    required String chapterTitle,
    required String cacheTag,
  }) async {
    _wirePlayerListeners();
    state.value = AudioState(
      stage: AudioStage.preparing,
      chapterTitle: chapterTitle,
    );

    try {
      // Reuse the locked Director to break text into segments + assign voices.
      final segments = _segmentize(text);
      final chapter = Chapter(index: 0, title: chapterTitle, segments: segments);

      final tts = TtsService.instance;
      await tts.init();
      final cacheDir = await tts.chapterCacheDir();
      final result = await tts.renderChapter(chapter: chapter, outputDir: cacheDir);

      await _player.setFilePath(result.wavFile.path);
      await _player.play();
      state.value = state.value.copyWith(
        stage: AudioStage.playing,
        duration: result.duration,
      );
    } catch (e) {
      state.value = state.value.copyWith(
        stage: AudioStage.error,
        error: e.toString(),
      );
    }
  }

  Future<void> pause() async {
    await _player.pause();
    state.value = state.value.copyWith(stage: AudioStage.paused);
  }

  Future<void> resume() async {
    await _player.play();
    state.value = state.value.copyWith(stage: AudioStage.playing);
  }

  Future<void> stop() async {
    await _player.stop();
    state.value = const AudioState();
  }

  Future<void> dispose() async {
    await _posSub?.cancel();
    await _durSub?.cancel();
    await _stateSub?.cancel();
    await _player.dispose();
  }

  /// Run the heuristic Director on a free-text blob to derive Segments. We
  /// reuse [BookAnalyzer]'s public dialogue parser by feeding it a synthetic
  /// single-chapter EPUB-shaped string isn't worth the ceremony — a tiny
  /// local copy of the rule keeps this file self-contained.
  List<Segment> _segmentize(String text) {
    final paragraphs = text
        .split(RegExp(r'\n{2,}|\r\n\r\n'))
        .map((p) => p.trim())
        .where((p) => p.isNotEmpty)
        .toList();

    final out = <Segment>[];
    var altMale = true;
    final quote = RegExp(r'(?:"([^"]+)"|“([^”]+)”|‘([^’]+)’)');

    for (final para in paragraphs.isEmpty ? [text.trim()] : paragraphs) {
      var cursor = 0;
      final matches = quote.allMatches(para).toList();
      if (matches.isEmpty) {
        out.add(Segment(text: para, voiceId: 'af_bella'));
        continue;
      }
      for (final m in matches) {
        if (m.start > cursor) {
          final pre = para.substring(cursor, m.start).trim();
          if (pre.isNotEmpty) out.add(Segment(text: pre, voiceId: 'af_bella'));
        }
        final dialogue = (m.group(1) ?? m.group(2) ?? m.group(3) ?? '').trim();
        if (dialogue.isNotEmpty) {
          out.add(Segment(
            text: dialogue,
            voiceId: altMale ? 'am_adam' : 'af_nicole',
          ));
          altMale = !altMale;
        }
        cursor = m.end;
      }
      if (cursor < para.length) {
        final tail = para.substring(cursor).trim();
        if (tail.isNotEmpty) out.add(Segment(text: tail, voiceId: 'af_bella'));
      }
    }
    return out;
  }
}
