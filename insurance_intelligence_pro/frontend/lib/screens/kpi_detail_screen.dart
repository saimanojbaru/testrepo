import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/company.dart';
import '../models/insight.dart';
import '../models/kpi.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';
import 'knowledge_detail_screen.dart';

/// Drill-down screen for a single KPI.
///
/// Shows: hero value + status, formula breakdown, source filing,
/// methodology, peer ranking, optional jump to related knowledge
/// article. Strict sourcing — every figure here ties back to the
/// 10-K it came from.
class KpiDetailScreen extends StatelessWidget {
  final Kpi kpi;
  final Company company;
  final int? fiscalYear;
  const KpiDetailScreen({
    super.key,
    required this.kpi,
    required this.company,
    this.fiscalYear,
  });

  @override
  Widget build(BuildContext context) {
    final statusColor = AppColors.statusColor(kpi.status);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(
          '${company.ticker} · ${kpi.label}',
          style: const TextStyle(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.w700,
            fontSize: 16,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          _Hero(kpi: kpi, company: company, statusColor: statusColor),
          const SizedBox(height: 18),
          if (kpi.formula != null) ...[
            const SectionHeader(title: 'Formula & inputs'),
            _FormulaCard(formula: kpi.formula!, statusColor: statusColor),
            const SizedBox(height: 18),
          ],
          if (kpi.source != null) ...[
            const SectionHeader(title: 'Source filing'),
            _SourceCard(source: kpi.source!),
            const SizedBox(height: 18),
          ],
          if ((kpi.methodology ?? '').isNotEmpty) ...[
            const SectionHeader(title: 'Methodology & nuance'),
            GlassCard(
              child: Text(kpi.methodology!,
                  style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                      height: 1.55)),
            ),
            const SizedBox(height: 18),
          ],
          _PeerRankCard(company: company, code: kpi.code),
          const SizedBox(height: 18),
          if (kpi.knowledgeSlug != null)
            _LearnMoreCard(slug: kpi.knowledgeSlug!),
        ],
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  final Kpi kpi;
  final Company company;
  final Color statusColor;
  const _Hero(
      {required this.kpi,
      required this.company,
      required this.statusColor});

  @override
  Widget build(BuildContext context) {
    final isPercent = kpi.unit == '%';
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  company.ticker,
                  style: const TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.2,
                    fontSize: 11.5,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                      color: statusColor.withValues(alpha: 0.5)),
                ),
                child: Text(
                  kpi.status.toUpperCase(),
                  style: TextStyle(
                      color: statusColor,
                      fontWeight: FontWeight.w800,
                      fontSize: 10,
                      letterSpacing: 1.4),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(kpi.label,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.4)),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                kpi.value == null
                    ? '–'
                    : (isPercent
                        ? Formatters.pct(kpi.value)
                        : Formatters.num1(kpi.value)),
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 36,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.5),
              ),
              if (!isPercent) ...[
                const SizedBox(width: 6),
                Text(kpi.unit,
                    style: const TextStyle(
                        color: AppColors.textMuted, fontSize: 14)),
              ],
              const SizedBox(width: 12),
              if (kpi.deltaYoy != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: statusColor.withValues(alpha: 0.45)),
                  ),
                  child: Text(
                    'YoY ${Formatters.signedPct(kpi.deltaYoy)}',
                    style: TextStyle(
                        color: statusColor,
                        fontWeight: FontWeight.w700,
                        fontSize: 12),
                  ),
                ),
            ],
          ),
          if (kpi.benchmark != null) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                const Icon(Icons.flag_outlined,
                    color: AppColors.textMuted, size: 14),
                const SizedBox(width: 6),
                Text(
                  'Industry benchmark · ${Formatters.pct(kpi.benchmark)}',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 12),
                ),
              ],
            ),
          ],
          if ((kpi.description ?? '').isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(kpi.description!,
                style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                    height: 1.5)),
          ],
        ],
      ),
    ).animate().fadeIn(duration: 350.ms).slideY(begin: 0.05);
  }
}

class _FormulaCard extends StatelessWidget {
  final KpiFormula formula;
  final Color statusColor;
  const _FormulaCard(
      {required this.formula, required this.statusColor});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surfaceHigh,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.border),
            ),
            child: Text(
              formula.expression,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontFamily: 'monospace',
                  fontSize: 13,
                  fontWeight: FontWeight.w700),
            ),
          ),
          const SizedBox(height: 14),
          _FormulaRow(label: 'Numerator', term: formula.numerator, value: formula.numeratorValue),
          const Divider(color: AppColors.divider, height: 16),
          _FormulaRow(label: 'Denominator', term: formula.denominator, value: formula.denominatorValue),
        ],
      ),
    );
  }
}

class _FormulaRow extends StatelessWidget {
  final String label;
  final String term;
  final String value;
  const _FormulaRow(
      {required this.label, required this.term, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
                color: AppColors.textMuted,
                fontSize: 10,
                letterSpacing: 1.4,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: Text(term,
                  style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600)),
            ),
            Text(value,
                style: const TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 13)),
          ],
        ),
      ],
    );
  }
}

class _SourceCard extends StatelessWidget {
  final FilingSource source;
  const _SourceCard({required this.source});

  Future<void> _open() async {
    if (source.url.isEmpty) return;
    final uri = Uri.tryParse(source.url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: source.url.isEmpty ? null : _open,
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: AppColors.accent.withValues(alpha: 0.4)),
            ),
            child: const Icon(Icons.description_outlined,
                color: AppColors.accent, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${source.form} · ${source.fiscalYear}',
                  style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontWeight: FontWeight.w700,
                      fontSize: 14),
                ),
                const SizedBox(height: 2),
                Text(
                  source.filedDate.isEmpty
                      ? 'SEC EDGAR filing'
                      : 'Filed ${source.filedDate} · SEC EDGAR',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11.5),
                ),
              ],
            ),
          ),
          if (source.url.isNotEmpty)
            const Icon(Icons.open_in_new_rounded,
                color: AppColors.accent, size: 16),
        ],
      ),
    );
  }
}

class _PeerRankCard extends StatelessWidget {
  final Company company;
  final String code;
  const _PeerRankCard({required this.company, required this.code});

  @override
  Widget build(BuildContext context) {
    final compare = AnalyticsService.instance.autoCompare(company.ticker);
    final metrics = (compare['metrics'] as List?) ?? [];
    final match = metrics.cast<Map<String, dynamic>>().firstWhere(
          (m) => m['code'] == code,
          orElse: () => <String, dynamic>{},
        );
    if (match.isEmpty) return const SizedBox.shrink();
    final values =
        ((match['values'] as List?) ?? []).cast<Map<String, dynamic>>();
    if (values.isEmpty) return const SizedBox.shrink();
    final lowerIsBetter = match['lower_is_better'] == true;
    final sorted = [...values]
      ..sort((a, b) {
        final av = (a['value'] as num?)?.toDouble() ?? 0;
        final bv = (b['value'] as num?)?.toDouble() ?? 0;
        return lowerIsBetter ? av.compareTo(bv) : bv.compareTo(av);
      });
    final me = sorted.indexWhere((v) => v['ticker'] == company.ticker);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SectionHeader(title: 'Peer ranking'),
        GlassCard(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Column(
            children: [
              if (me >= 0)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(
                    '${company.ticker} ranks #${me + 1} of ${sorted.length} '
                    '(${lowerIsBetter ? "lower is better" : "higher is better"})',
                    style: const TextStyle(
                        color: AppColors.accent,
                        fontWeight: FontWeight.w700,
                        fontSize: 12.5),
                  ),
                ),
              for (var i = 0; i < sorted.length; i++)
                _PeerRow(
                    rank: i + 1,
                    ticker: sorted[i]['ticker'].toString(),
                    value: sorted[i]['value'] as num?,
                    isMe: sorted[i]['ticker'] == company.ticker),
            ],
          ),
        ),
      ],
    );
  }
}

class _PeerRow extends StatelessWidget {
  final int rank;
  final String ticker;
  final num? value;
  final bool isMe;
  const _PeerRow(
      {required this.rank,
      required this.ticker,
      required this.value,
      required this.isMe});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
      decoration: BoxDecoration(
        color: isMe
            ? AppColors.accent.withValues(alpha: 0.08)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 26,
            child: Text('#$rank',
                style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 11,
                    fontWeight: FontWeight.w700)),
          ),
          SizedBox(
            width: 60,
            child: Text(ticker,
                style: TextStyle(
                    color: isMe ? AppColors.accent : AppColors.textPrimary,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.2)),
          ),
          const Spacer(),
          Text(
            value == null ? '–' : Formatters.pct(value),
            style: TextStyle(
                color: isMe ? AppColors.accent : AppColors.textPrimary,
                fontSize: 13,
                fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

class _LearnMoreCard extends StatelessWidget {
  final String slug;
  const _LearnMoreCard({required this.slug});

  @override
  Widget build(BuildContext context) {
    final all = AnalyticsService.instance.knowledge();
    final raw = all.firstWhere((a) => a['slug'] == slug,
        orElse: () => <String, dynamic>{});
    if (raw.isEmpty) return const SizedBox.shrink();
    final article =
        KnowledgeArticle.fromJson(Map<String, dynamic>.from(raw));
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => KnowledgeDetailScreen(article: article))),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.menu_book_rounded,
                color: Colors.white, size: 18),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('LEARN MORE',
                    style: TextStyle(
                        color: AppColors.accent,
                        fontSize: 10,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w800)),
                const SizedBox(height: 4),
                Text(article.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 14)),
                const SizedBox(height: 2),
                Text(article.framework,
                    style: const TextStyle(
                        color: AppColors.textMuted, fontSize: 11.5)),
              ],
            ),
          ),
          const Icon(Icons.arrow_forward_ios_rounded,
              color: AppColors.textMuted, size: 14),
        ],
      ),
    );
  }
}
