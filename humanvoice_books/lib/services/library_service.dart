import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:epubx/epubx.dart' as epub;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/book.dart';

/// Persists the user's imported EPUB library and extracts cover images.
///
/// Storage layout:
///   getApplicationSupportDirectory()/library/<bookId>/cover.png
///   SharedPreferences['library.books'] = JSON list of Book entities
class LibraryService {
  LibraryService._();
  static final LibraryService instance = LibraryService._();

  static const _kPrefsKey = 'library.books';

  Future<List<Book>> loadAll() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_kPrefsKey);
    if (raw == null || raw.isEmpty) return const [];
    try {
      return Book.decodeAll(raw);
    } catch (_) {
      return const [];
    }
  }

  Future<void> _saveAll(List<Book> books) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kPrefsKey, Book.encodeAll(books));
  }

  /// Imports an EPUB from [sourcePath]. Parses metadata + extracts cover.
  /// Returns the persisted [Book]. Idempotent on the same file (returns the
  /// existing entry by id).
  Future<Book> import(String sourcePath) async {
    final id = _idFor(sourcePath);
    final existing = (await loadAll()).where((b) => b.id == id).toList();
    if (existing.isNotEmpty) return existing.first;

    final bytes = await File(sourcePath).readAsBytes();
    final book = await epub.EpubReader.readBook(bytes);

    final dir = await _bookDir(id);
    String? coverPath;
    final coverImage = book.CoverImage;
    if (coverImage != null) {
      // epubx returns a `Image` from the `image` package. We don't want to
      // pull that whole dependency just for encoding, so we walk the EPUB
      // resources manually for any image whose href matches the cover hint.
      final cover = _findCoverBytes(book);
      if (cover != null) {
        final out = File('${dir.path}/cover.png');
        await out.writeAsBytes(cover);
        coverPath = out.path;
      }
    } else {
      final cover = _findCoverBytes(book);
      if (cover != null) {
        final out = File('${dir.path}/cover.png');
        await out.writeAsBytes(cover);
        coverPath = out.path;
      }
    }

    final entry = Book(
      id: id,
      filePath: sourcePath,
      title: (book.Title ?? sourcePath.split('/').last).trim(),
      author: book.Author?.trim(),
      coverPath: coverPath,
      importedAt: DateTime.now(),
    );

    final all = await loadAll();
    all.insert(0, entry);
    await _saveAll(all);
    return entry;
  }

  Future<void> updateProgress(String id, int page) async {
    final all = await loadAll();
    final idx = all.indexWhere((b) => b.id == id);
    if (idx < 0) return;
    all[idx] = all[idx].copyWith(lastReadPage: page);
    await _saveAll(all);
  }

  Future<void> remove(String id) async {
    final all = await loadAll();
    all.removeWhere((b) => b.id == id);
    await _saveAll(all);
    try {
      final dir = await _bookDir(id);
      if (await dir.exists()) await dir.delete(recursive: true);
    } catch (_) {}
  }

  // ----- helpers -----

  String _idFor(String filePath) =>
      sha256.convert(utf8.encode(filePath)).toString().substring(0, 16);

  Future<Directory> _bookDir(String id) async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/library/$id');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  /// Best-effort cover extraction. Walks the EPUB's image resources and picks
  /// the first one whose href contains "cover" (case-insensitive); falls back
  /// to the first image overall. Returns the raw image bytes or null.
  List<int>? _findCoverBytes(epub.EpubBook book) {
    final images = book.Content?.Images;
    if (images == null || images.isEmpty) return null;
    epub.EpubByteContentFile? pick;
    for (final entry in images.entries) {
      if (entry.key.toLowerCase().contains('cover')) {
        pick = entry.value;
        break;
      }
    }
    pick ??= images.values.first;
    return pick.Content;
  }
}
