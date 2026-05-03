import 'dart:io';

import 'package:epubx/epubx.dart' as epub;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/book.dart';
import '../services/audio_service.dart';
import '../services/library_service.dart';
import '../services/model_service.dart';

enum ReaderTheme { light, sepia, dark }

class _ReaderThemeData {
  final Color background;
  final Color text;
  final Color accent;
  const _ReaderThemeData(this.background, this.text, this.accent);

  static const light = _ReaderThemeData(
    Color(0xFFFCFAF6), // crisp white-paper
    Color(0xFF1B1B1B),
    Color(0xFF7E3FB0),
  );
  static const sepia = _ReaderThemeData(
    Color(0xFFF4ECD8), // warm paper
    Color(0xFF3E2C1A),
    Color(0xFF8C5A2B),
  );
  static const dark = _ReaderThemeData(
    Color(0xFF000000), // true black, OLED friendly
    Color(0xFFE8E6E1),
    Color(0xFFFFB070),
  );

  static _ReaderThemeData of(ReaderTheme t) {
    switch (t) {
      case ReaderTheme.light:
        return light;
      case ReaderTheme.sepia:
        return sepia;
      case ReaderTheme.dark:
        return dark;
    }
  }
}

class _ChapterPage {
  final String title;
  final String html;
  final String plainText;
  const _ChapterPage(this.title, this.html, this.plainText);
}

class ReaderScreen extends StatefulWidget {
  final Book book;
  const ReaderScreen({super.key, required this.book});

  @override
  State<ReaderScreen> createState() => _ReaderScreenState();
}

class _ReaderScreenState extends State<ReaderScreen> {
  late final PageController _controller;
  List<_ChapterPage>? _chapters;
  String? _loadError;
  bool _showOverlay = false;
  ReaderTheme _theme = ReaderTheme.sepia;
  double _fontScale = 1.0;
  int _currentPage = 0;

  static const _kThemeKey = 'reader.theme';
  static const _kFontKey = 'reader.fontScale';

  @override
  void initState() {
    super.initState();
    _controller = PageController(initialPage: widget.book.lastReadPage);
    _currentPage = widget.book.lastReadPage;

    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _restorePrefs();
    _loadEpub();
  }

  @override
  void dispose() {
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.manual,
      overlays: SystemUiOverlay.values,
    );
    _controller.dispose();
    super.dispose();
  }

  Future<void> _restorePrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final t = prefs.getInt(_kThemeKey);
    final fs = prefs.getDouble(_kFontKey);
    if (!mounted) return;
    setState(() {
      if (t != null && t >= 0 && t < ReaderTheme.values.length) {
        _theme = ReaderTheme.values[t];
      }
      if (fs != null) _fontScale = fs.clamp(0.8, 1.6);
    });
  }

  Future<void> _saveTheme(ReaderTheme t) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_kThemeKey, t.index);
  }

  Future<void> _saveFontScale(double f) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_kFontKey, f);
  }

  Future<void> _loadEpub() async {
    try {
      final pages = await compute(_parseEpub, widget.book.filePath);
      if (!mounted) return;
      setState(() => _chapters = pages);
    } catch (e) {
      if (!mounted) return;
      setState(() => _loadError = '$e');
    }
  }

  void _onPageChanged(int page) {
    setState(() => _currentPage = page);
    LibraryService.instance.updateProgress(widget.book.id, page);
  }

  void _toggleOverlay() => setState(() => _showOverlay = !_showOverlay);

  void _previousPage() {
    if (_currentPage > 0) {
      _controller.previousPage(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    }
  }

  void _nextPage() {
    final total = _chapters?.length ?? 0;
    if (_currentPage + 1 < total) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    }
  }

  Future<void> _openThemeSheet() async {
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: _ReaderThemeData.of(_theme).background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => _ThemeSheet(
        currentTheme: _theme,
        currentScale: _fontScale,
        onTheme: (t) {
          setState(() => _theme = t);
          _saveTheme(t);
        },
        onScale: (s) {
          setState(() => _fontScale = s);
          _saveFontScale(s);
        },
      ),
    );
  }

  Future<void> _readCurrentChapter() async {
    final ch = _chapters?[_currentPage];
    if (ch == null) return;
    if (ch.plainText.trim().isEmpty) return;

    // Don't re-synthesize while a render or playback is already underway —
    // the mini-player owns those controls. Re-entry would silently spawn
    // a parallel TTS render and double-bill the CPU.
    final stage = AudioService.instance.state.value.stage;
    if (stage == AudioStage.preparing ||
        stage == AudioStage.playing ||
        stage == AudioStage.paused) {
      return;
    }

    // Model gate: surface a clear snackbar instead of letting TtsService.init
    // throw a "missing kokoro model" StateError that ends up in the mini-player.
    final ready = await ModelService.instance.kokoroReady();
    if (!mounted) return;
    if (!ready) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Voice engine not installed. Tap the Library card to download.',
          ),
        ),
      );
      return;
    }

    await AudioService.instance.playRawText(
      text: ch.plainText,
      chapterTitle: ch.title,
      cacheTag: '${widget.book.id}_$_currentPage',
    );
  }

  @override
  Widget build(BuildContext context) {
    final td = _ReaderThemeData.of(_theme);
    return Scaffold(
      backgroundColor: td.background,
      body: Stack(
        children: [
          if (_chapters == null && _loadError == null)
            const Center(child: CircularProgressIndicator())
          else if (_loadError != null)
            _LoadError(message: _loadError!, color: td.text)
          else
            _ReaderPager(
              chapters: _chapters!,
              controller: _controller,
              theme: td,
              fontScale: _fontScale,
              onPageChanged: _onPageChanged,
              onTapLeft: _previousPage,
              onTapRight: _nextPage,
              onTapCenter: _toggleOverlay,
            ),
          // Top + bottom overlays
          if (_chapters != null) ...[
            _TopOverlay(
              visible: _showOverlay,
              theme: td,
              title: _chapters![_currentPage].title,
              onBack: () => Navigator.pop(context),
              onTheme: _openThemeSheet,
            ),
            _BottomOverlay(
              visible: _showOverlay,
              theme: td,
              currentPage: _currentPage,
              totalPages: _chapters!.length,
              onPlay: _readCurrentChapter,
            ),
            const _AudioMiniPlayer(),
          ],
        ],
      ),
    );
  }
}

// ====== isolate-side EPUB parser ======

Future<List<_ChapterPage>> _parseEpub(String path) async {
  final bytes = await File(path).readAsBytes();
  final book = await epub.EpubReader.readBook(bytes);
  final flat = <epub.EpubChapter>[];
  void walk(epub.EpubChapter c) {
    flat.add(c);
    for (final s in c.SubChapters ?? const <epub.EpubChapter>[]) {
      walk(s);
    }
  }
  for (final c in book.Chapters ?? const <epub.EpubChapter>[]) {
    walk(c);
  }
  return [
    for (var i = 0; i < flat.length; i++)
      _ChapterPage(
        (flat[i].Title ?? 'Chapter ${i + 1}').trim(),
        flat[i].HtmlContent ?? '',
        _stripHtml(flat[i].HtmlContent ?? ''),
      ),
  ];
}

String _stripHtml(String html) {
  final noTags = html.replaceAll(RegExp(r'<[^>]+>'), ' ');
  return noTags
      .replaceAll('&nbsp;', ' ')
      .replaceAll('&amp;', '&')
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&quot;', '"')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

// ====== widgets ======

class _ReaderPager extends StatelessWidget {
  final List<_ChapterPage> chapters;
  final PageController controller;
  final _ReaderThemeData theme;
  final double fontScale;
  final ValueChanged<int> onPageChanged;
  final VoidCallback onTapLeft;
  final VoidCallback onTapRight;
  final VoidCallback onTapCenter;

  const _ReaderPager({
    required this.chapters,
    required this.controller,
    required this.theme,
    required this.fontScale,
    required this.onPageChanged,
    required this.onTapLeft,
    required this.onTapRight,
    required this.onTapCenter,
  });

  @override
  Widget build(BuildContext context) {
    return PageView.builder(
      controller: controller,
      itemCount: chapters.length,
      onPageChanged: onPageChanged,
      itemBuilder: (ctx, i) {
        final ch = chapters[i];
        return LayoutBuilder(
          builder: (ctx, constraints) {
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTapUp: (details) {
                final w = constraints.maxWidth;
                final dx = details.localPosition.dx;
                if (dx < w * 0.20) {
                  onTapLeft();
                } else if (dx > w * 0.80) {
                  onTapRight();
                } else {
                  onTapCenter();
                }
              },
              child: SafeArea(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(28, 80, 28, 100),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        ch.title,
                        style: GoogleFonts.literata(
                          color: theme.text,
                          fontSize: 24 * fontScale,
                          fontWeight: FontWeight.w600,
                          height: 1.3,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Html(
                        data: ch.html,
                        style: {
                          'body': Style(
                            color: theme.text,
                            fontFamily: GoogleFonts.literata().fontFamily,
                            fontSize: FontSize(17 * fontScale),
                            lineHeight: const LineHeight(1.6),
                            margin: Margins.zero,
                            padding: HtmlPaddings.zero,
                          ),
                          'p': Style(
                            margin: Margins.only(bottom: 16),
                          ),
                          'h1, h2, h3': Style(
                            color: theme.text,
                            fontFamily: GoogleFonts.literata().fontFamily,
                            fontWeight: FontWeight.w600,
                          ),
                          'a': Style(
                            color: theme.accent,
                            textDecoration: TextDecoration.none,
                          ),
                          'img': Style(
                            display: Display.none, // skip embedded images for v1
                          ),
                        },
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }
}

class _TopOverlay extends StatelessWidget {
  final bool visible;
  final _ReaderThemeData theme;
  final String title;
  final VoidCallback onBack;
  final VoidCallback onTheme;

  const _TopOverlay({
    required this.visible,
    required this.theme,
    required this.title,
    required this.onBack,
    required this.onTheme,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedPositioned(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      top: visible ? 0 : -120,
      left: 0,
      right: 0,
      child: Material(
        color: theme.background.withOpacity(0.92),
        child: SafeArea(
          bottom: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Row(
              children: [
                IconButton(
                  icon: Icon(Icons.arrow_back, color: theme.text),
                  onPressed: onBack,
                ),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.literata(
                      color: theme.text,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                IconButton(
                  icon: Icon(Icons.text_fields, color: theme.text),
                  onPressed: onTheme,
                  tooltip: 'Theme & font',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BottomOverlay extends StatelessWidget {
  final bool visible;
  final _ReaderThemeData theme;
  final int currentPage;
  final int totalPages;
  final VoidCallback onPlay;

  const _BottomOverlay({
    required this.visible,
    required this.theme,
    required this.currentPage,
    required this.totalPages,
    required this.onPlay,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedPositioned(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      bottom: visible ? 0 : -160,
      left: 0,
      right: 0,
      child: Material(
        color: theme.background.withOpacity(0.92),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Chapter ${currentPage + 1} of $totalPages',
                        style: GoogleFonts.inter(
                          color: theme.text.withOpacity(0.7),
                          fontSize: 12,
                        ),
                      ),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(2),
                        child: LinearProgressIndicator(
                          value: totalPages == 0
                              ? 0
                              : (currentPage + 1) / totalPages,
                          minHeight: 3,
                          backgroundColor:
                              theme.text.withOpacity(0.08),
                          color: theme.accent,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ValueListenableBuilder<AudioState>(
                  valueListenable: AudioService.instance.state,
                  builder: (ctx, s, _) {
                    // Mini-player owns controls once audio is live; the FAB is
                    // only the "kick off rendering" affordance.
                    if (s.stage == AudioStage.playing ||
                        s.stage == AudioStage.paused) {
                      return const SizedBox.shrink();
                    }
                    final preparing = s.stage == AudioStage.preparing;
                    return FloatingActionButton.small(
                      heroTag: 'play_audio_fab',
                      backgroundColor: theme.accent,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      onPressed: preparing ? null : onPlay,
                      child: preparing
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.play_arrow),
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ThemeSheet extends StatelessWidget {
  final ReaderTheme currentTheme;
  final double currentScale;
  final ValueChanged<ReaderTheme> onTheme;
  final ValueChanged<double> onScale;

  const _ThemeSheet({
    required this.currentTheme,
    required this.currentScale,
    required this.onTheme,
    required this.onScale,
  });

  @override
  Widget build(BuildContext context) {
    final td = _ReaderThemeData.of(currentTheme);
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: td.text.withOpacity(0.2),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Text('Theme',
              style: GoogleFonts.inter(
                color: td.text,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
              )),
          const SizedBox(height: 12),
          Row(
            children: [
              for (final t in ReaderTheme.values) ...[
                Expanded(
                  child: _ThemeChip(
                    theme: t,
                    selected: t == currentTheme,
                    onTap: () => onTheme(t),
                  ),
                ),
                if (t != ReaderTheme.values.last) const SizedBox(width: 12),
              ],
            ],
          ),
          const SizedBox(height: 24),
          Text('Font size',
              style: GoogleFonts.inter(
                color: td.text,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
              )),
          Slider(
            min: 0.8,
            max: 1.6,
            divisions: 8,
            value: currentScale,
            label: '${(currentScale * 100).round()}%',
            activeColor: td.accent,
            onChanged: onScale,
          ),
        ],
      ),
    );
  }
}

class _ThemeChip extends StatelessWidget {
  final ReaderTheme theme;
  final bool selected;
  final VoidCallback onTap;
  const _ThemeChip({
    required this.theme,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final td = _ReaderThemeData.of(theme);
    final label = switch (theme) {
      ReaderTheme.light => 'Paper',
      ReaderTheme.sepia => 'Sepia',
      ReaderTheme.dark => 'OLED',
    };
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 18),
        decoration: BoxDecoration(
          color: td.background,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: selected ? td.accent : td.text.withOpacity(0.15),
            width: selected ? 2 : 1,
          ),
        ),
        child: Center(
          child: Text(
            label,
            style: GoogleFonts.literata(
              color: td.text,
              fontSize: 15,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }
}

class _AudioMiniPlayer extends StatelessWidget {
  const _AudioMiniPlayer();

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AudioState>(
      valueListenable: AudioService.instance.state,
      builder: (ctx, s, _) {
        if (s.stage == AudioStage.idle) return const SizedBox.shrink();
        return Positioned(
          left: 16,
          right: 16,
          bottom: 100,
          child: Material(
            elevation: 8,
            borderRadius: BorderRadius.circular(18),
            color: const Color(0xFF1B1B1F),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
              child: Row(
                children: [
                  if (s.stage == AudioStage.preparing)
                    const SizedBox(
                      width: 32,
                      height: 32,
                      child: Padding(
                        padding: EdgeInsets.all(6),
                        child: CircularProgressIndicator(strokeWidth: 2.5),
                      ),
                    )
                  else
                    IconButton(
                      onPressed: s.stage == AudioStage.playing
                          ? AudioService.instance.pause
                          : AudioService.instance.resume,
                      icon: Icon(
                        s.stage == AudioStage.playing
                            ? Icons.pause
                            : Icons.play_arrow,
                        color: Colors.white,
                      ),
                    ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          s.chapterTitle ?? 'Reading',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: GoogleFonts.inter(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _statusLine(s),
                          style: GoogleFonts.inter(
                            color: Colors.white.withOpacity(0.6),
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: AudioService.instance.stop,
                    icon: const Icon(Icons.stop, color: Colors.white70),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  String _statusLine(AudioState s) {
    switch (s.stage) {
      case AudioStage.preparing:
        return 'Synthesizing voice…';
      case AudioStage.playing:
      case AudioStage.paused:
        final pos = _fmt(s.position);
        final dur = _fmt(s.duration);
        return '$pos / $dur';
      case AudioStage.error:
        return s.error ?? 'Audio error';
      case AudioStage.idle:
        return '';
    }
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    final h = d.inHours;
    return h > 0 ? '$h:$m:$s' : '$m:$s';
  }
}

class _LoadError extends StatelessWidget {
  final String message;
  final Color color;
  const _LoadError({required this.message, required this.color});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, color: color, size: 48),
            const SizedBox(height: 12),
            Text(
              'Could not open this EPUB',
              style: GoogleFonts.literata(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: color,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 12,
                color: color.withOpacity(0.7),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
