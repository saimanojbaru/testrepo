import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/local_data.dart';
import '../models/standard.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

/// Level 2 — Comprehensive detail screen for a single standard.
///
/// Renders the four required sections: Entire Chapter Content, Need /
/// Objective, Evolution timeline, and Granular GAAP-vs-STAT
/// Comparison.
class StandardDetailScreen extends StatelessWidget {
  final Standard standard;
  const StandardDetailScreen({super.key, required this.standard});

  Future<void> _openSource() async {
    final catalog = LocalData.instance.catalogFor(standard.framework);
    final uri = Uri.tryParse(catalog.sourceUrl);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  String get _frameworkLabel {
    switch (standard.framework) {
      case 'SSAP':
        return 'SSAP';
      case 'ASC944':
        return 'ASC 944';
      case 'PCAOB':
        return 'PCAOB AS';
      default:
        return standard.framework;
    }
  }

  @override
  Widget build(BuildContext context) {
    final catalog = LocalData.instance.catalogFor(standard.framework);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(
          '$_frameworkLabel ${standard.number}',
          style: const TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w800,
              fontSize: 16),
        ),
        actions: [
          IconButton(
            tooltip: 'Open primary source',
            icon: const Icon(Icons.open_in_new_rounded,
                color: AppColors.accent),
            onPressed: _openSource,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          _Hero(standard: standard, catalog: catalog),
          const SizedBox(height: 18),

          // Section 1: Entire Chapter Content
          const SectionHeader(
            title: '1. Chapter content',
            subtitle: 'Full text of the standard',
          ),
          GlassCard(
            child: Text(
              standard.chapterContent,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.6),
            ),
          ).animate().fadeIn(duration: 350.ms),
          const SizedBox(height: 18),

          // Section 2: Need / Objective
          const SectionHeader(
            title: '2. Need & objective',
            subtitle: 'Why the standard exists',
          ),
          GlassCard(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF18284A), Color(0xFF0B1426)],
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.accent.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                        color: AppColors.accent.withValues(alpha: 0.4)),
                  ),
                  child: const Icon(Icons.flag_outlined,
                      color: AppColors.accent, size: 18),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    standard.need,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        height: 1.55),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),

          // Section 3: Evolution
          if (standard.evolution.isNotEmpty) ...[
            const SectionHeader(
              title: '3. Evolution',
              subtitle: 'Timeline of standard updates',
            ),
            GlassCard(
              padding: const EdgeInsets.symmetric(
                  horizontal: 16, vertical: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var i = 0; i < standard.evolution.length; i++)
                    _TimelineEntry(
                      entry: standard.evolution[i],
                      isLast: i == standard.evolution.length - 1,
                    ),
                ],
              ),
            ),
            const SizedBox(height: 18),
          ],

          // Section 4: GAAP vs STAT comparison
          if (standard.gaapComparison.isNotEmpty) ...[
            const SectionHeader(
              title: '4. GAAP vs STAT comparison',
              subtitle: 'Granular line-by-line differences',
            ),
            GlassCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  for (var i = 0; i < standard.gaapComparison.length; i++)
                    _ComparisonRow(
                      row: standard.gaapComparison[i],
                      isLast: i == standard.gaapComparison.length - 1,
                    ),
                ],
              ),
            ),
            const SizedBox(height: 18),
          ],

          // Tags
          if (standard.tags.isNotEmpty) ...[
            const SectionHeader(title: 'Tags'),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: standard.tags
                  .map((t) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceElevated,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: AppColors.border),
                        ),
                        child: Text(t,
                            style: const TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 11,
                                fontWeight: FontWeight.w600)),
                      ))
                  .toList(),
            ),
            const SizedBox(height: 18),
          ],

          // Source footer
          Center(
            child: Column(
              children: [
                Text(
                  catalog.publisher,
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 11,
                      fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 2),
                Text(
                  catalog.manual,
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 10),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  final Standard standard;
  final StandardsCatalog catalog;
  const _Hero({required this.standard, required this.catalog});

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
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${catalog.framework == "SSAP" ? "SSAP" : catalog.framework == "ASC944" ? "ASC 944" : "PCAOB"} ${standard.number}',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      letterSpacing: 1.2,
                      fontWeight: FontWeight.w800),
                ),
              ),
              if ((standard.fsli ?? '').isNotEmpty)
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceHigh,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Text('FSLI · ${standard.fsli}',
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontSize: 10,
                          letterSpacing: 1.2,
                          fontWeight: FontWeight.w700)),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            standard.title,
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 22,
                height: 1.25),
          ),
          const SizedBox(height: 10),
          Text(standard.summary,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13.5,
                  height: 1.55)),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms).slideY(begin: 0.05);
  }
}

class _TimelineEntry extends StatelessWidget {
  final StandardEvolution entry;
  final bool isLast;
  const _TimelineEntry({required this.entry, required this.isLast});

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 8,
                height: 8,
                margin: const EdgeInsets.only(top: 4),
                decoration: const BoxDecoration(
                    color: AppColors.accent, shape: BoxShape.circle),
              ),
              if (!isLast)
                Expanded(
                  child: Container(
                    width: 1,
                    margin: const EdgeInsets.only(top: 2),
                    color: AppColors.divider,
                  ),
                ),
            ],
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(entry.year,
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontWeight: FontWeight.w800,
                          fontSize: 12,
                          letterSpacing: 1.2)),
                  const SizedBox(height: 4),
                  Text(entry.change,
                      style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 12.5,
                          height: 1.5)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ComparisonRow extends StatefulWidget {
  final GaapComparisonRow row;
  final bool isLast;
  const _ComparisonRow({required this.row, required this.isLast});

  @override
  State<_ComparisonRow> createState() => _ComparisonRowState();
}

class _ComparisonRowState extends State<_ComparisonRow> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Container(
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
                      child: Text(widget.row.aspect,
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
                const SizedBox(height: 6),
                if (!_expanded)
                  Text(
                    widget.row.delta,
                    style: const TextStyle(
                        color: AppColors.accent,
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        height: 1.4),
                  )
                else ...[
                  _Pane(label: 'GAAP', body: widget.row.gaap),
                  const SizedBox(height: 8),
                  _Pane(label: 'STAT (SAP)', body: widget.row.stat),
                  const SizedBox(height: 8),
                  _Pane(
                    label: 'KEY DELTA',
                    body: widget.row.delta,
                    accent: true,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Pane extends StatelessWidget {
  final String label;
  final String body;
  final bool accent;
  const _Pane({
    required this.label,
    required this.body,
    this.accent = false,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 84,
          child: Text(label,
              style: TextStyle(
                  color: accent ? AppColors.accent : AppColors.textMuted,
                  fontSize: 10,
                  letterSpacing: 1.4,
                  fontWeight: FontWeight.w700)),
        ),
        Expanded(
          child: Text(body,
              style: TextStyle(
                  color: accent
                      ? AppColors.textPrimary
                      : AppColors.textSecondary,
                  fontSize: 12.5,
                  height: 1.45,
                  fontWeight:
                      accent ? FontWeight.w600 : FontWeight.w500)),
        ),
      ],
    );
  }
}
