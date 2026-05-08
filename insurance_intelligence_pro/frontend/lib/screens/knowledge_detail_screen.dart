import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/insight.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

/// Full-screen drill-down for a single knowledge article.
///
/// Renders header, summary, GAAP-vs-STAT cards, FSLI table, sections,
/// references with tappable URLs, and a "last updated" footer.
class KnowledgeDetailScreen extends StatelessWidget {
  final KnowledgeArticle article;
  const KnowledgeDetailScreen({super.key, required this.article});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text(
          'Knowledge',
          style: TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w700,
              fontSize: 16,
              letterSpacing: 0.5),
        ),
        actions: [
          if (article.references.isNotEmpty)
            IconButton(
              tooltip: 'Open primary source',
              icon: const Icon(Icons.open_in_new_rounded,
                  color: AppColors.accent),
              onPressed: () => _open(article.references.first.url),
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          _Header(article: article),
          const SizedBox(height: 18),
          if ((article.keyDifference ?? '').isNotEmpty) ...[
            const SectionHeader(title: 'Key difference'),
            GlassCard(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF18284A), Color(0xFF0B1426)],
              ),
              child: Text(
                article.keyDifference!,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    height: 1.5),
              ),
            ).animate().fadeIn(duration: 350.ms),
            const SizedBox(height: 18),
          ],
          if ((article.gaapView ?? '').isNotEmpty ||
              (article.statView ?? '').isNotEmpty) ...[
            const SectionHeader(title: 'GAAP vs Statutory'),
            _GaapStatCard(
                gaap: article.gaapView ?? '–', stat: article.statView ?? '–'),
            const SizedBox(height: 18),
          ],
          if (article.fsliTable.isNotEmpty) ...[
            const SectionHeader(
                title: 'FSLI comparison',
                subtitle: 'Line-item level GAAP vs STAT differences'),
            _FsliTable(rows: article.fsliTable),
            const SizedBox(height: 18),
          ],
          if (article.bodyMd.isNotEmpty) ...[
            const SectionHeader(title: 'Deep dive'),
            GlassCard(child: _MarkdownBody(text: article.bodyMd)),
            const SizedBox(height: 18),
          ],
          for (final s in article.sections) ...[
            SectionHeader(title: s.heading),
            GlassCard(child: _SectionBody(section: s)),
            const SizedBox(height: 18),
          ],
          if (article.references.isNotEmpty) ...[
            const SectionHeader(title: 'References & primary sources'),
            GlassCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  for (var i = 0; i < article.references.length; i++)
                    _ReferenceTile(
                      reference: article.references[i],
                      isLast: i == article.references.length - 1,
                    )
                ],
              ),
            ),
            const SizedBox(height: 18),
          ],
          if (article.tags.isNotEmpty) ...[
            const SectionHeader(title: 'Tags'),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: article.tags
                  .map((t) => _TagChip(label: t))
                  .toList(),
            ),
            const SizedBox(height: 16),
          ],
          if ((article.lastUpdated ?? '').isNotEmpty)
            Center(
              child: Text(
                'Last updated ${article.lastUpdated}',
                style: const TextStyle(color: AppColors.textMuted, fontSize: 11),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _open(String url) async {
    if (url.isEmpty) return;
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }
}

class _Header extends StatelessWidget {
  final KnowledgeArticle article;
  const _Header({required this.article});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              _MetaChip(
                  label: article.framework,
                  filled: true,
                  color: AppColors.accent),
              if ((article.fsli ?? '').isNotEmpty)
                _MetaChip(label: 'FSLI · ${article.fsli}'),
              _MetaChip(label: article.depth.toUpperCase()),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            article.title,
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 22,
                height: 1.25),
          ),
          const SizedBox(height: 10),
          Text(
            article.summary,
            style: const TextStyle(
                color: AppColors.textSecondary, fontSize: 13.5, height: 1.5),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05);
  }
}

class _MetaChip extends StatelessWidget {
  final String label;
  final bool filled;
  final Color? color;
  const _MetaChip(
      {required this.label, this.filled = false, this.color});

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.textMuted;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: filled ? c.withValues(alpha: 0.18) : AppColors.surfaceHigh,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
            color: filled ? c.withValues(alpha: 0.6) : AppColors.border),
      ),
      child: Text(
        label,
        style: TextStyle(
            color: filled ? c : AppColors.textSecondary,
            fontSize: 10.5,
            letterSpacing: 1.2,
            fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _GaapStatCard extends StatelessWidget {
  final String gaap;
  final String stat;
  const _GaapStatCard({required this.gaap, required this.stat});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: _Pane(label: 'GAAP', body: gaap)),
            const VerticalDivider(
                color: AppColors.divider, width: 16, thickness: 1),
            Expanded(child: _Pane(label: 'STAT (SAP)', body: stat)),
          ],
        ),
      ),
    );
  }
}

class _Pane extends StatelessWidget {
  final String label;
  final String body;
  const _Pane({required this.label, required this.body});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
                color: AppColors.accent,
                fontSize: 11,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4)),
        const SizedBox(height: 6),
        Text(body,
            style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 12.5,
                height: 1.5)),
      ],
    );
  }
}

class _FsliTable extends StatelessWidget {
  final List<FsliRow> rows;
  const _FsliTable({required this.rows});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          for (var i = 0; i < rows.length; i++)
            _FsliRowTile(row: rows[i], isLast: i == rows.length - 1),
        ],
      ),
    );
  }
}

class _FsliRowTile extends StatefulWidget {
  final FsliRow row;
  final bool isLast;
  const _FsliRowTile({required this.row, required this.isLast});

  @override
  State<_FsliRowTile> createState() => _FsliRowTileState();
}

class _FsliRowTileState extends State<_FsliRowTile> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      decoration: BoxDecoration(
        border: !widget.isLast
            ? const Border(bottom: BorderSide(color: AppColors.divider))
            : null,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => setState(() => _expanded = !_expanded),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(widget.row.lineItem,
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w700,
                              fontSize: 13)),
                    ),
                    Icon(
                      _expanded
                          ? Icons.expand_less_rounded
                          : Icons.expand_more_rounded,
                      color: AppColors.textMuted,
                      size: 18,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                AnimatedCrossFade(
                  duration: const Duration(milliseconds: 200),
                  crossFadeState: _expanded
                      ? CrossFadeState.showSecond
                      : CrossFadeState.showFirst,
                  firstChild: Text(
                    widget.row.delta,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                        height: 1.4),
                  ),
                  secondChild: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _FsliCell(label: 'GAAP', value: widget.row.gaap),
                      const SizedBox(height: 8),
                      _FsliCell(label: 'STAT', value: widget.row.stat),
                      const SizedBox(height: 8),
                      _FsliCell(
                          label: 'KEY DELTA',
                          value: widget.row.delta,
                          accent: true),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _FsliCell extends StatelessWidget {
  final String label;
  final String value;
  final bool accent;
  const _FsliCell({
    required this.label,
    required this.value,
    this.accent = false,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 78,
          child: Text(label,
              style: TextStyle(
                  color: accent ? AppColors.accent : AppColors.textMuted,
                  fontSize: 10,
                  letterSpacing: 1.4,
                  fontWeight: FontWeight.w700)),
        ),
        Expanded(
          child: Text(value,
              style: TextStyle(
                  color: accent
                      ? AppColors.textPrimary
                      : AppColors.textSecondary,
                  fontSize: 12.5,
                  height: 1.45,
                  fontWeight: accent ? FontWeight.w600 : FontWeight.w500)),
        ),
      ],
    );
  }
}

class _SectionBody extends StatelessWidget {
  final KnowledgeSection section;
  const _SectionBody({required this.section});

  @override
  Widget build(BuildContext context) {
    if (section.kind == 'list' && section.items.isNotEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: section.items
            .map((it) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Padding(
                        padding: EdgeInsets.only(top: 7, right: 8),
                        child: SizedBox(
                          width: 4,
                          height: 4,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: AppColors.accent,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      ),
                      Expanded(
                        child: Text(it,
                            style: const TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 13,
                                height: 1.5)),
                      ),
                    ],
                  ),
                ))
            .toList(),
      );
    }
    final body = section.content ?? '';
    return Text(
      body,
      style: const TextStyle(
          color: AppColors.textSecondary, fontSize: 13, height: 1.55),
    );
  }
}

class _ReferenceTile extends StatelessWidget {
  final KnowledgeReference reference;
  final bool isLast;
  const _ReferenceTile(
      {required this.reference, required this.isLast});

  Future<void> _open() async {
    final uri = Uri.tryParse(reference.url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: _open,
        child: Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            border: !isLast
                ? const Border(bottom: BorderSide(color: AppColors.divider))
                : null,
          ),
          child: Row(
            children: [
              const Icon(Icons.menu_book_outlined,
                  color: AppColors.accent, size: 18),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(reference.label,
                        style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.w600,
                            fontSize: 13)),
                    const SizedBox(height: 2),
                    Text(reference.url,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 11)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const Icon(Icons.open_in_new_rounded,
                  color: AppColors.textMuted, size: 16),
            ],
          ),
        ),
      ),
    );
  }
}

class _TagChip extends StatelessWidget {
  final String label;
  const _TagChip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AppColors.surfaceElevated,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.border),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: AppColors.textSecondary,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.4),
      ),
    );
  }
}

class _MarkdownBody extends StatelessWidget {
  final String text;
  const _MarkdownBody({required this.text});

  @override
  Widget build(BuildContext context) {
    final widgets = <Widget>[];
    final lines = text.split('\n');
    var inCode = false;
    final codeBuf = StringBuffer();
    for (final raw in lines) {
      final l = raw.trimRight();
      if (l.startsWith('```')) {
        if (inCode) {
          widgets.add(_codeBlock(codeBuf.toString()));
          codeBuf.clear();
        }
        inCode = !inCode;
        continue;
      }
      if (inCode) {
        codeBuf.writeln(raw);
        continue;
      }
      if (l.isEmpty) {
        widgets.add(const SizedBox(height: 8));
        continue;
      }
      if (l.startsWith('## ')) {
        widgets.add(Padding(
          padding: const EdgeInsets.only(top: 12, bottom: 6),
          child: Text(l.substring(3),
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w800,
                  fontSize: 16)),
        ));
      } else if (l.startsWith('- ') || l.startsWith('* ')) {
        final body = l.substring(2).replaceAll('**', '');
        widgets.add(Padding(
          padding: const EdgeInsets.fromLTRB(8, 2, 0, 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 7, right: 8),
                child: SizedBox(
                  width: 4,
                  height: 4,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: AppColors.accent,
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: Text(body,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.5)),
              ),
            ],
          ),
        ));
      } else {
        widgets.add(Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Text(l.replaceAll('**', '').replaceAll('`', ''),
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.55)),
        ));
      }
    }
    if (codeBuf.isNotEmpty) {
      widgets.add(_codeBlock(codeBuf.toString()));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: widgets,
    );
  }

  Widget _codeBlock(String code) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      margin: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.surfaceHigh,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.border),
      ),
      child: Text(
        code.trimRight(),
        style: const TextStyle(
            color: AppColors.textPrimary,
            fontFamily: 'monospace',
            fontSize: 12,
            height: 1.45),
      ),
    );
  }
}
