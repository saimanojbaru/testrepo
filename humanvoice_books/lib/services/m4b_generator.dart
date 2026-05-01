import 'dart:async';
import 'dart:io';

import 'package:ffmpeg_kit_flutter_audio/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_audio/return_code.dart';
import 'package:path_provider/path_provider.dart';

import '../models/segment.dart';

class ChapterAsset {
  final Chapter chapter;
  final File wav;
  final Duration duration;
  ChapterAsset(this.chapter, this.wav, this.duration);
}

/// Combines per-chapter WAVs into a single .m4b (AAC) with chapter markers.
///
/// Output layout:
///   - {appSupport}/audiobooks/{book_slug}.m4b
///   - {appSupport}/audiobooks/{book_slug}.chapters.txt   (ffmetadata)
class M4bGenerator {
  M4bGenerator._();
  static final M4bGenerator instance = M4bGenerator._();

  Future<File> mux({
    required String bookTitle,
    required String author,
    required List<ChapterAsset> chapters,
    void Function(double pct)? onProgress,
  }) async {
    if (chapters.isEmpty) {
      throw ArgumentError('cannot mux an empty book');
    }

    final outDir = await _outputDir();
    final slug = _slug(bookTitle);
    final concatList = File('${outDir.path}/$slug.concat.txt');
    final metadata = File('${outDir.path}/$slug.chapters.txt');
    final out = File('${outDir.path}/$slug.m4b');

    // 1. Build the ffmpeg concat file.
    final cb = StringBuffer();
    for (final c in chapters) {
      cb.writeln("file '${c.wav.path.replaceAll("'", r"'\''")}'");
    }
    await concatList.writeAsString(cb.toString());

    // 2. Build the ffmetadata chapters file.
    final mb = StringBuffer()
      ..writeln(';FFMETADATA1')
      ..writeln('title=$bookTitle')
      ..writeln('artist=$author')
      ..writeln('album=$bookTitle')
      ..writeln('genre=Audiobook');
    var cursorMs = 0;
    for (final c in chapters) {
      final start = cursorMs;
      final end = cursorMs + c.duration.inMilliseconds;
      mb
        ..writeln('[CHAPTER]')
        ..writeln('TIMEBASE=1/1000')
        ..writeln('START=$start')
        ..writeln('END=$end')
        ..writeln('title=${_escapeMeta(c.chapter.title)}');
      cursorMs = end;
    }
    await metadata.writeAsString(mb.toString());

    // 3. Run ffmpeg: concat WAVs -> AAC LC, attach chapter metadata, .m4b container.
    // Pass arguments as a list so we don't depend on the kit's shell parsing.
    final args = <String>[
      '-y',
      '-f', 'concat',
      '-safe', '0',
      '-i', concatList.path,
      '-i', metadata.path,
      '-map_metadata', '1',
      '-c:a', 'aac',
      '-b:a', '64k',
      '-ar', '24000',
      '-ac', '1',
      '-movflags', '+faststart',
      '-f', 'mp4',
      out.path,
    ];

    final completer = Completer<File>();
    final totalMs = cursorMs;
    FFmpegKit.executeWithArgumentsAsync(
      args,
      (session) async {
        final code = await session.getReturnCode();
        if (ReturnCode.isSuccess(code)) {
          completer.complete(out);
        } else {
          final logs = await session.getAllLogsAsString();
          completer.completeError(StateError('ffmpeg failed: $logs'));
        }
      },
      null,
      (stat) {
        if (onProgress != null && totalMs > 0) {
          final timeMs = stat.getTime();
          onProgress((timeMs / totalMs).clamp(0.0, 1.0));
        }
      },
    );

    final result = await completer.future;
    // Best-effort cleanup of temp files; not fatal if it fails.
    try {
      await concatList.delete();
      await metadata.delete();
    } catch (_) {}
    return result;
  }

  Future<Directory> _outputDir() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/audiobooks');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  /// Returns every previously-generated .m4b in the library directory,
  /// newest first. Safe to call before any audiobook has been generated.
  Future<List<File>> listLibrary() async {
    final dir = await _outputDir();
    final files = <File>[];
    await for (final entry in dir.list()) {
      if (entry is File && entry.path.toLowerCase().endsWith('.m4b')) {
        files.add(entry);
      }
    }
    files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
    return files;
  }

  static String _slug(String s) {
    final cleaned = s.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '_');
    final trimmed = cleaned.replaceAll(RegExp(r'^_+|_+$'), '');
    return trimmed.isEmpty ? 'audiobook' : trimmed;
  }

  static String _escapeMeta(String s) =>
      s.replaceAll(r'\', r'\\').replaceAll('=', r'\=').replaceAll('\n', ' ');
}
