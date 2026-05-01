import 'dart:async';
import 'dart:io';

import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../models/segment.dart';
import '../services/book_analyzer.dart';
import '../services/m4b_generator.dart';
import '../services/model_service.dart';
import '../services/tts_service.dart';

// ===== events =====
sealed class BookEvent extends Equatable {
  const BookEvent();
  @override
  List<Object?> get props => const [];
}

class EnsureModels extends BookEvent {
  const EnsureModels();
}

class ImportEpub extends BookEvent {
  final String path;
  const ImportEpub(this.path);
  @override
  List<Object?> get props => [path];
}

class StartProduction extends BookEvent {
  final String title;
  final String author;
  const StartProduction({required this.title, required this.author});
  @override
  List<Object?> get props => [title, author];
}

class RefreshLibrary extends BookEvent {
  const RefreshLibrary();
}

// ===== state =====
enum Stage {
  idle,
  downloadingModels,
  modelsReady,
  analyzing,
  rendering,
  muxing,
  done,
  error,
}

class BookState extends Equatable {
  final Stage stage;
  final double progress; // 0..1
  final String message;
  final List<Chapter> chapters;
  final File? outputM4b;
  final List<File> library;
  final String? loadedEpubName;

  const BookState({
    this.stage = Stage.idle,
    this.progress = 0,
    this.message = '',
    this.chapters = const [],
    this.outputM4b,
    this.library = const [],
    this.loadedEpubName,
  });

  BookState copyWith({
    Stage? stage,
    double? progress,
    String? message,
    List<Chapter>? chapters,
    File? outputM4b,
    List<File>? library,
    String? loadedEpubName,
  }) =>
      BookState(
        stage: stage ?? this.stage,
        progress: progress ?? this.progress,
        message: message ?? this.message,
        chapters: chapters ?? this.chapters,
        outputM4b: outputM4b ?? this.outputM4b,
        library: library ?? this.library,
        loadedEpubName: loadedEpubName ?? this.loadedEpubName,
      );

  @override
  List<Object?> get props => [
        stage,
        progress,
        message,
        chapters,
        outputM4b,
        library,
        loadedEpubName,
      ];
}

// ===== bloc =====
class BookBloc extends Bloc<BookEvent, BookState> {
  String? _epubPath;

  BookBloc() : super(const BookState()) {
    on<EnsureModels>(_onEnsureModels);
    on<ImportEpub>(_onImport);
    on<StartProduction>(_onStart);
    on<RefreshLibrary>(_onRefreshLibrary);
  }

  Future<void> _onRefreshLibrary(RefreshLibrary _, Emitter<BookState> emit) async {
    final files = await M4bGenerator.instance.listLibrary();
    emit(state.copyWith(library: files));
  }

  Future<void> _onEnsureModels(EnsureModels e, Emitter<BookState> emit) async {
    emit(state.copyWith(stage: Stage.downloadingModels, message: 'Checking models'));
    try {
      await for (final p in ModelService.instance.ensureAll()) {
        final pct = p.$2 > 0 ? p.$1 / p.$2 : 0.0;
        emit(state.copyWith(progress: pct, message: p.$3));
      }
      emit(state.copyWith(stage: Stage.modelsReady, progress: 1, message: 'Models ready'));
    } catch (err) {
      emit(state.copyWith(stage: Stage.error, message: '$err'));
    }
  }

  Future<void> _onImport(ImportEpub e, Emitter<BookState> emit) async {
    _epubPath = e.path;
    final name = e.path.split('/').last;
    emit(state.copyWith(
      message: 'Loaded: $name',
      chapters: const [],
      loadedEpubName: name,
    ));
  }

  Future<void> _onStart(StartProduction e, Emitter<BookState> emit) async {
    final epubPath = _epubPath;
    if (epubPath == null) {
      emit(state.copyWith(stage: Stage.error, message: 'Pick an EPUB first'));
      return;
    }
    if (!await ModelService.instance.kokoroReady()) {
      emit(state.copyWith(
        stage: Stage.error,
        message: 'Kokoro model not downloaded. Tap Initialize Models first.',
      ));
      return;
    }

    // ---- Phase A: Director (analysis) ----
    emit(state.copyWith(stage: Stage.analyzing, progress: 0, chapters: const []));
    final analyzed = <Chapter>[];
    int total = 0;
    await for (final ev in BookAnalyzer.analyze(epubPath)) {
      if (ev is AnalyzerProgress) {
        total = ev.chapterCount;
        emit(state.copyWith(
          message: 'Directing: ${ev.chapterTitle}',
          progress: total == 0 ? 0 : ev.chapterIndex / total,
        ));
      } else if (ev is AnalyzerChapter) {
        analyzed.add(ev.chapter);
        emit(state.copyWith(chapters: List.unmodifiable(analyzed)));
      } else if (ev is AnalyzerError) {
        emit(state.copyWith(stage: Stage.error, message: ev.message));
        return;
      }
    }

    // ---- Phase B: Enactor (render) with chapter pipelining ----
    emit(state.copyWith(stage: Stage.rendering, progress: 0, message: 'Rendering chapters'));
    final tts = TtsService.instance;
    await tts.init();
    final cacheDir = await _cacheDir();
    final assets = <ChapterAsset>[];
    for (var i = 0; i < analyzed.length; i++) {
      final c = analyzed[i];
      emit(state.copyWith(
        progress: i / analyzed.length,
        message: 'Rendering ${c.title}',
      ));

      // Background-render the next chapter while we finalize this one.
      Future<String>? next;
      if (i + 1 < analyzed.length) {
        next = tts.renderChapterInBackground(analyzed[i + 1]);
      }

      final r = await tts.renderChapter(chapter: c, outputDir: cacheDir);
      assets.add(ChapterAsset(c, r.wavFile, r.duration));

      if (next != null) {
        // Wait for the prefetch so it doesn't pile up GPU/CPU work — its WAV
        // is now cached and the next iteration's renderChapter will skip
        // re-doing work because the file is already on disk.
        await next;
      }
    }

    // ---- Phase C: Muxer ----
    emit(state.copyWith(stage: Stage.muxing, progress: 0, message: 'Building .m4b'));
    try {
      final m4b = await M4bGenerator.instance.mux(
        bookTitle: e.title,
        author: e.author,
        chapters: assets,
        onProgress: (p) => emit(state.copyWith(progress: p)),
      );
      final library = await M4bGenerator.instance.listLibrary();
      emit(state.copyWith(
        stage: Stage.done,
        progress: 1,
        message: 'Saved: ${m4b.path}',
        outputM4b: m4b,
        library: library,
      ));
    } catch (err) {
      emit(state.copyWith(stage: Stage.error, message: '$err'));
    }
  }

  Future<Directory> _cacheDir() async {
    // Mirrors TtsService's cache layout so we hit the same WAVs.
    final base = await ModelService.instance.kokoroDir();
    final root = Directory('${base.parent.parent.path}/audiobook_cache');
    if (!await root.exists()) await root.create(recursive: true);
    return root;
  }
}
