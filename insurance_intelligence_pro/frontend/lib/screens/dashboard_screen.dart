import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/api_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/animated_loader.dart';
import '../widgets/error_card.dart';
import '../widgets/glass_card.dart';
import '../widgets/insight_card.dart';
import '../widgets/section_header.dart';
import '../widgets/sparkline.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  late Future<({MarketPulse pulse, List<NewsItem> news})> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<({MarketPulse pulse, List<NewsItem> news})> _load(
      {bool refresh = false}) async {
    final pulse = await ApiService.instance.dashboardPulse(refresh: refresh);
    final news = await ApiService.instance.dashboardTopNews(limit: 5);
    return (pulse: pulse, news: news);
  }

  Future<void> _refresh() async {
    final fut = _load(refresh: true);
    setState(() => _future = fut);
    await fut;
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        color: AppColors.accent,
        backgroundColor: AppColors.surface,
        onRefresh: _refresh,
        child: FutureBuilder<({MarketPulse pulse, List<NewsItem> news})>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return ListView(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 120),
                children: const [
                  _DashboardHeader(),
                  SizedBox(height: 28),
                  ShimmerCard(height: 120),
                  SizedBox(height: 16),
                  ShimmerCard(height: 220),
                  SizedBox(height: 16),
                  ShimmerCard(height: 220),
                ],
              );
            }
            if (snap.hasError) {
              return ListView(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 120),
                children: [
                  const _DashboardHeader(),
                  const SizedBox(height: 24),
                  ErrorCard(message: '${snap.error}', onRetry: _refresh),
                ],
              );
            }
            final pulse = snap.data!.pulse;
            final news = snap.data!.news;
            return ListView(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 120),
              children: [
                const _DashboardHeader(),
                const SizedBox(height: 24),
                _PulseHero(headline: pulse.headline),
                const SizedBox(height: 20),
                const SectionHeader(
                  title: 'Market Pulse',
                  subtitle: 'Insurance-specific signals updated continuously',
                ),
                _Indicators(
                    indicators: pulse.indicators, sparklines: pulse.sparklines),
                const SizedBox(height: 24),
                const SectionHeader(
                  title: 'Today\'s Insights',
                  subtitle: 'Conclusions, not data — pick your move',
                ),
                ...List.generate(
                  pulse.insights.length,
                  (i) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child:
                        InsightCard(insight: pulse.insights[i], index: i),
                  ),
                ),
                const SizedBox(height: 16),
                const SectionHeader(
                  title: 'Top Headlines',
                  subtitle: 'Curated from public industry feeds',
                ),
                ...List.generate(
                  news.length,
                  (i) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _NewsTile(item: news[i], index: i),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _DashboardHeader extends StatelessWidget {
  const _DashboardHeader();

  @override
  Widget build(BuildContext context) {
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
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
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
                decoration: const BoxDecoration(
                    color: AppColors.positive, shape: BoxShape.circle),
              ),
              const SizedBox(width: 6),
              Text('LIVE',
                  style: TextStyle(
                      color: AppColors.positive,
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.6)),
            ],
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
                Text(
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
                    fontSize: 18,
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
                    color: AppColors.primary.withOpacity(0.4),
                    blurRadius: 18,
                    spreadRadius: 1),
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
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(ind.label,
                  style: TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10.5,
                      letterSpacing: 0.6,
                      fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 6),
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
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
              if (spark.isNotEmpty) Sparkline(data: spark, color: color, height: 24),
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
                        style: TextStyle(
                            color: AppColors.accent,
                            fontSize: 10,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(width: 8),
                    Text(item.category,
                        style: TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                    const Spacer(),
                    Text(Formatters.date(item.published),
                        style: TextStyle(
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
