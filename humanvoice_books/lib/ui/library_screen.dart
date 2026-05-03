import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

import '../models/book.dart';
import '../services/library_service.dart';
import '../services/model_service.dart';
import 'reader_screen.dart';

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  List<Book> _books = const [];
  bool _loading = true;
  bool _modelReady = false;
  bool _modelDownloading = false;
  double _modelProgress = 0;
  String _modelMessage = '';

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    await [Permission.notification].request();
    await _refresh();
  }

  Future<void> _refresh() async {
    final ready = await ModelService.instance.kokoroReady();
    final books = await LibraryService.instance.loadAll();
    if (!mounted) return;
    setState(() {
      _modelReady = ready;
      _books = books;
      _loading = false;
    });
  }

  Future<void> _initModels() async {
    if (_modelDownloading) return;
    setState(() {
      _modelDownloading = true;
      _modelProgress = 0;
      _modelMessage = 'Starting…';
    });
    try {
      await for (final p in ModelService.instance.ensureAll()) {
        if (!mounted) return;
        final pct = p.$2 > 0 ? p.$1 / p.$2 : 0.0;
        setState(() {
          _modelProgress = pct;
          _modelMessage = p.$3;
        });
      }
      if (!mounted) return;
      setState(() {
        _modelDownloading = false;
        _modelReady = true;
        _modelMessage = 'Ready';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _modelDownloading = false;
        _modelMessage = 'Failed: $e';
      });
    }
  }

  Future<void> _importEpub() async {
    // Accept the common EPUB-family extensions. Some Android file managers
    // serve the raw bytes through a content:// URI without a usable path —
    // withData ensures we always get the bytes back, which we'll spool to
    // app-private storage before parsing.
    final r = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['epub', 'epub3', 'kepub'],
      withData: true,
    );
    if (r == null || r.files.isEmpty) return;
    final picked = r.files.single;

    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Importing…')));
    try {
      // Prefer the OS path if file_picker resolved one. Otherwise spool
      // the bytes (or read from openReadStream) into app-private storage
      // and import from there — gives us a stable absolute path the
      // reader can re-open later.
      String path;
      if (picked.path != null && picked.path!.isNotEmpty) {
        path = picked.path!;
      } else {
        final bytes = picked.bytes;
        if (bytes == null) {
          throw StateError('File picker returned neither path nor bytes');
        }
        final dir = await getApplicationSupportDirectory();
        final inbox = Directory('${dir.path}/inbox');
        if (!await inbox.exists()) await inbox.create(recursive: true);
        final safeName = picked.name.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_');
        final out = File('${inbox.path}/$safeName');
        await out.writeAsBytes(bytes, flush: true);
        path = out.path;
      }
      await LibraryService.instance.import(path);
      await _refresh();
    } on FormatException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Not a valid EPUB: $e'),
      ));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Import failed: $e')));
    }
  }

  void _openBook(Book b) {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => ReaderScreen(book: b)),
    );
  }

  Future<void> _confirmRemove(Book b) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Remove book?'),
        content: Text('Remove "${b.title}" from your library? The original EPUB on disk is left untouched.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok == true) {
      await LibraryService.instance.remove(b.id);
      await _refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : SafeArea(
              child: CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(24, 24, 24, 8),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Library',
                            style: GoogleFonts.literata(
                              fontSize: 34,
                              fontWeight: FontWeight.w600,
                              letterSpacing: -0.5,
                              color: theme.colorScheme.onSurface,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${_books.length} ${_books.length == 1 ? "book" : "books"}',
                            style: GoogleFonts.inter(
                              fontSize: 14,
                              color: theme.colorScheme.onSurface.withOpacity(0.6),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: _ModelStatusCard(
                        ready: _modelReady,
                        downloading: _modelDownloading,
                        progress: _modelProgress,
                        message: _modelMessage,
                        onTap: _modelReady ? null : _initModels,
                      ),
                    ),
                  ),
                  if (_books.isEmpty)
                    SliverFillRemaining(
                      hasScrollBody: false,
                      child: _EmptyLibrary(onImport: _importEpub),
                    )
                  else
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
                      sliver: SliverGrid(
                        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                          maxCrossAxisExtent: 180,
                          mainAxisSpacing: 16,
                          crossAxisSpacing: 16,
                          childAspectRatio: 0.62,
                        ),
                        delegate: SliverChildBuilderDelegate(
                          (ctx, i) => _BookTile(
                            book: _books[i],
                            onTap: () => _openBook(_books[i]),
                            onLongPress: () => _confirmRemove(_books[i]),
                          ),
                          childCount: _books.length,
                        ),
                      ),
                    ),
                ],
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _importEpub,
        icon: const Icon(Icons.add),
        label: const Text('Add EPUB'),
      ),
    );
  }
}

class _ModelStatusCard extends StatelessWidget {
  final bool ready;
  final bool downloading;
  final double progress;
  final String message;
  final VoidCallback? onTap;

  const _ModelStatusCard({
    required this.ready,
    required this.downloading,
    required this.progress,
    required this.message,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = ready
        ? const Color(0xFF2A9D8F)
        : (downloading ? theme.colorScheme.primary : const Color(0xFFE85D75));
    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(
                  ready
                      ? Icons.graphic_eq
                      : (downloading ? Icons.downloading : Icons.cloud_download),
                  color: color,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      ready
                          ? 'Voice engine ready'
                          : (downloading ? 'Downloading voice engine…' : 'Voice engine not installed'),
                      style: GoogleFonts.inter(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      ready
                          ? 'Kokoro-82M INT8 · "Read to Me" enabled'
                          : (downloading
                              ? message
                              : 'Tap to download Kokoro-82M INT8 (~80 MB)'),
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        color: theme.colorScheme.onSurface.withOpacity(0.65),
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (downloading) ...[
                      const SizedBox(height: 8),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: progress == 0 ? null : progress,
                          minHeight: 4,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              if (!downloading)
                Icon(Icons.chevron_right,
                    color: theme.colorScheme.onSurface.withOpacity(0.4)),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyLibrary extends StatelessWidget {
  final VoidCallback onImport;
  const _EmptyLibrary({required this.onImport});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.menu_book_outlined,
              size: 80,
              color: Theme.of(context).colorScheme.onSurface.withOpacity(0.25)),
          const SizedBox(height: 16),
          Text(
            'Your shelf is empty',
            style: GoogleFonts.literata(
              fontSize: 22,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Import an EPUB to start reading.\nTap "Add EPUB" below.',
            textAlign: TextAlign.center,
            style: GoogleFonts.inter(
              fontSize: 14,
              color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
            ),
          ),
        ],
      ),
    );
  }
}

class _BookTile extends StatelessWidget {
  final Book book;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _BookTile({
    required this.book,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: book.coverPath != null && File(book.coverPath!).existsSync()
                  ? Image.file(File(book.coverPath!), fit: BoxFit.cover)
                  : _CoverPlaceholder(book: book),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            book.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: GoogleFonts.literata(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              height: 1.3,
              color: theme.colorScheme.onSurface,
            ),
          ),
          if (book.author != null && book.author!.isNotEmpty)
            Text(
              book.author!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.inter(
                fontSize: 11,
                color: theme.colorScheme.onSurface.withOpacity(0.6),
              ),
            ),
        ],
      ),
    );
  }
}

class _CoverPlaceholder extends StatelessWidget {
  final Book book;
  const _CoverPlaceholder({required this.book});

  @override
  Widget build(BuildContext context) {
    final palette = [
      [const Color(0xFF2A1B5C), const Color(0xFF7E3FB0)],
      [const Color(0xFFE85D75), const Color(0xFFFF8A4C)],
      [const Color(0xFF1C5C84), const Color(0xFF2A9D8F)],
      [const Color(0xFF4D3A2F), const Color(0xFFB07A4F)],
    ];
    final stripe = palette[book.id.hashCode.abs() % palette.length];
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: stripe,
        ),
      ),
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Text(
            book.title,
            maxLines: 5,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: GoogleFonts.literata(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.w600,
              height: 1.2,
            ),
          ),
        ),
      ),
    );
  }
}
