import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../services/api_service.dart';
import '../widgets/insight_card.dart';
import '../widgets/section_header.dart';
import '../widgets/sparkline.dart';
import 'news_detail_screen.dart';
import 'settings_screen.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final pulse = AnalyticsService.instance.pulse();
    final mp = MarketPulse.fromJson(pulse);
    final headlines = AnalyticsService.instance
        .topNews(limit: 5)
        .map((e) => NewsItem.fromJson(e))
        .toList();

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 24, 20, 120),
        children: [
          const _DashboardHeader(),
          const SizedBox(height: 24),
          _PulseHero(headline: mp.headline),
          const SizedBox(height: 20),
          const SectionHeader(
            title: 'Market Pulse',
            subtitle: 'Insurance-specific signals updated continuously',
          ),
          _Indicators(indicators: mp.indicators, sparklines: mp.sparklines),
          const SizedBox(height: 24),
          const SectionHeader(
            title: 'Today\'s Insights',
            subtitle: 'Conclusions, not data — pick your move',
          ),
          ...List.generate(
            mp.insights.length,
            (i) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: InsightCard(
                insight: mp.insights[i],
                index: i,
                onTap: () => _showInsightDetail(context, mp.insights[i]),
              ),
            ),
          ),
          const SizedBox(height: 8),
          const SectionHeader(
            title: 'Top Headlines',
            subtitle: 'Curated from public industry sources',
          ),
          ...List.generate(
            headlines.length,
            (i) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _NewsTile(item: headlines[i], index: i),
            ),
          ),
        ],
      ),
    );
  }
}

class _DashboardHeader extends StatefulWidget {
  const _DashboardHeader();

  @override
  State<_DashboardHeader> createState() => _DashboardHeaderState();
}

class _DashboardHeaderState extends State<_DashboardHeader> {
  bool? _live;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    if (!ApiService.instance.isConfigured) {
      if (mounted) setState(() => _live = false);
      return;
    }
    final ok = await ApiService.instance.probe();
    if (mounted) setState(() => _live = ok);
  }

  @override
  Widget build(BuildContext context) {
    final isConfigured = ApiService.instance.isConfigured;
    final live = _live == true && isConfigured;
    final color = live ? AppColors.positive : AppColors.warning;
    final label = live
        ? 'LIVE'
        : (isConfigured ? 'OFFLINE' : 'CURATED');
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.bolt_rounded, color: Colors.white, size: 18),
        ),
        const SizedBox(width: 12),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Insurance Intelligence',
                style: TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w800,
                  fontSize: 20,
                  letterSpacing: -0.4,
                ),
              ),
              Text(
                'Pro · v1.0',
                style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 11,
                    letterSpacing: 1.4,
                    fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
        InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () async {
            await Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => const SettingsScreen()));
            _refresh();
          },
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                      color: color, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(label,
                    style: TextStyle(
                        color: color,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.6)),
                const SizedBox(width: 6),
                const Icon(Icons.settings_outlined,
                    color: AppColors.textMuted, size: 12),
              ],
            ),
          ),
        ),
      ],
    ).animate().fadeIn(duration: 400.ms).slideY(begin: -0.1);
  }
}

class _PulseHero extends StatelessWidget {
  final String headline;
  const _PulseHero({required this.headline});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF1A2A48), Color(0xFF0B1426)],
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'TODAY\'S TAKE',
                  style: TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 11,
                    letterSpacing: 1.6,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  headline,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w700,
                    fontSize: 17,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: AppColors.primaryGradient,
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withValues(alpha: 0.4),
                  blurRadius: 18,
                  spreadRadius: 1,
                ),
              ],
            ),
            child: const Icon(Icons.auto_graph_rounded,
                color: Colors.white, size: 22),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 500.ms).slideY(begin: 0.1);
  }
}

class _Indicators extends StatelessWidget {
  final List<TrendIndicator> indicators;
  final List<List<double>> sparklines;
  const _Indicators({required this.indicators, required this.sparklines});

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: indicators.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.55,
      ),
      itemBuilder: (context, i) {
        final ind = indicators[i];
        final isUp = ind.direction == 'up';
        final positiveSignal = ind.label.contains('Yield') ||
            ind.label.contains('Issuance') ||
            ind.label.contains('Star');
        final color = isUp
            ? (positiveSignal ? AppColors.positive : AppColors.warning)
            : (positiveSignal ? AppColors.warning : AppColors.positive);
        final spark = sparklines.isNotEmpty
            ? sparklines[i % sparklines.length]
            : <double>[];
        return GlassCard(
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
          onTap: () => _showIndicatorDetail(context, ind, spark, color),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(ind.label,
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10.5,
                      letterSpacing: 0.6,
                      fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 6),
              Row(
                children: [
                  Text(ind.value,
                      style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 18,
                          fontWeight: FontWeight.w800)),
                  const SizedBox(width: 8),
                  Icon(
                    isUp
                        ? Icons.arrow_upward_rounded
                        : Icons.arrow_downward_rounded,
                    color: color,
                    size: 14,
                  ),
                  if (ind.delta != null)
                    Text(
                      Formatters.signedPct(ind.delta! * 100),
                      style: TextStyle(
                          color: color,
                          fontSize: 11,
                          fontWeight: FontWeight.w700),
                    ),
                ],
              ),
              const Spacer(),
              if (spark.isNotEmpty)
                Sparkline(data: spark, color: color, height: 24),
            ],
          ),
        ).animate().fadeIn(duration: 300.ms, delay: (60 * i).ms);
      },
    );
  }
}

class _NewsTile extends StatelessWidget {
  final NewsItem item;
  final int index;
  const _NewsTile({required this.item, required this.index});

  Color _impactColor() {
    switch (item.impact) {
      case 'High':
        return AppColors.negative;
      case 'Low':
        return AppColors.textMuted;
      default:
        return AppColors.warning;
    }
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => NewsDetailScreen(item: item))),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 4,
            height: 36,
            decoration: BoxDecoration(
              color: _impactColor(),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(item.source.toUpperCase(),
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontSize: 10,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(width: 8),
                    Text(item.category,
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                    const Spacer(),
                    Text(Formatters.date(item.published),
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                  ],
                ),
                const SizedBox(height: 6),
                Text(item.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                        height: 1.3)),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (60 * index).ms);
  }
}

void _showInsightDetail(BuildContext context, Insight insight) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppColors.background,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (_) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(insight.title,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 19,
                    height: 1.25)),
            if ((insight.detail ?? '').isNotEmpty) ...[
              const SizedBox(height: 10),
              Text(insight.detail!,
                  style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13.5,
                      height: 1.55)),
            ],
            if (insight.tags.isNotEmpty) ...[
              const SizedBox(height: 14),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: insight.tags
                    .map((t) => Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: AppColors.border),
                          ),
                          child: Text(t.toUpperCase(),
                              style: const TextStyle(
                                  color: AppColors.textMuted,
                                  fontSize: 10,
                                  letterSpacing: 1.2,
                                  fontWeight: FontWeight.w700)),
                        ))
                    .toList(),
              ),
            ],
          ],
        ),
      ),
    ),
  );
}

void _showIndicatorDetail(BuildContext context, TrendIndicator ind,
    List<double> spark, Color color) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppColors.background,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (_) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(ind.label,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 19)),
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(ind.value,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 28)),
                const SizedBox(width: 10),
                if (ind.delta != null)
                  Text(
                    Formatters.signedPct(ind.delta! * 100),
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w700,
                        fontSize: 14),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            if (spark.isNotEmpty)
              SizedBox(
                  height: 80,
                  child: Sparkline(data: spark, color: color, height: 80)),
            const SizedBox(height: 14),
            Text(
              _indicatorRationale(ind.label),
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.55),
            ),
            const SizedBox(height: 14),
            const Text(
              'Source · Treasury / NAIC / industry trade publications · Updated daily.',
              style: TextStyle(color: AppColors.textMuted, fontSize: 11),
            ),
          ],
        ),
      ),
    ),
  );
}

String _indicatorRationale(String label) {
  if (label.contains('10Y Treasury')) {
    return 'The 10-year Treasury yield is the dominant driver of life-insurer book yields and the discount rate underlying GAAP LDTI Liability for Future Policy Benefits. Higher rates lift investment income and lower LFPB through OCI.';
  }
  if (label.contains('Claims Inflation')) {
    return 'P&C claims inflation reflects severity in repair, medical, and litigation costs. Sustained elevation pressures combined ratios and forces rate filings.';
  }
  if (label.contains('Auto Severity')) {
    return 'Auto severity captures the rising cost per claim driven by vehicle complexity, medical inflation, and litigation finance. The single largest driver of personal-lines combined ratio in 2022–2024.';
  }
  if (label.contains('Cat Bond')) {
    return 'Catastrophe-bond issuance is a leading indicator of reinsurance capacity. Strong issuance signals investor appetite for insurance risk and softer retro pricing.';
  }
  if (label.contains('MA Star')) {
    return 'Medicare Advantage Star Ratings determine ~5% bonus payments and marketing limits. Falling 4+ Star share compresses Medicare-Advantage carrier margins and triggers rebate exposure.';
  }
  return 'Macro indicator influencing insurance-industry economics. Watch for trend changes — they typically lag into combined ratios with a 1–2 quarter delay.';
}
