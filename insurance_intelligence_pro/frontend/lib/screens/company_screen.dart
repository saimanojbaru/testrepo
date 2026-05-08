import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/company.dart';
import '../models/kpi.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../widgets/insight_card.dart';
import '../widgets/kpi_card.dart';
import '../widgets/risk_radar_chart.dart';
import '../widgets/score_ring.dart';
import '../widgets/section_header.dart';
import '../widgets/trend_chart.dart';

class CompanyScreen extends StatefulWidget {
  const CompanyScreen({super.key});

  @override
  State<CompanyScreen> createState() => _CompanyScreenState();
}

class _CompanyScreenState extends State<CompanyScreen> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focus = FocusNode();
  Timer? _debounce;
  CompanyAnalysis? _analysis;
  List<Map<String, dynamic>> _suggestions = [];
  final Set<int> _visibleSeries = {0, 1, 4};

  @override
  void initState() {
    super.initState();
    _analysis = AnalyticsService.instance.analyzeCompany('PGR');
    _controller.addListener(_onChanged);
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged() {
    _debounce?.cancel();
    final q = _controller.text.trim();
    if (q.isEmpty) {
      setState(() => _suggestions = []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 150), () {
      setState(() => _suggestions =
          AnalyticsService.instance.suggest(q, limit: 8));
    });
  }

  void _analyze(String query) {
    _focus.unfocus();
    setState(() {
      _analysis = AnalyticsService.instance.analyzeCompany(query);
      _suggestions = [];
    });
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 120),
        children: [
          _SearchHeader(
            controller: _controller,
            focusNode: _focus,
            onSubmit: _analyze,
          ),
          const SizedBox(height: 12),
          if (_suggestions.isNotEmpty)
            _Suggestions(
              suggestions: _suggestions,
              onTap: (q) {
                _controller.text = q;
                _analyze(q);
              },
            ),
          const SizedBox(height: 16),
          if (_analysis != null)
            _AnalysisBody(
              analysis: _analysis!,
              visible: _visibleSeries,
              onToggleSeries: (i) {
                setState(() {
                  if (_visibleSeries.contains(i)) {
                    if (_visibleSeries.length > 1) _visibleSeries.remove(i);
                  } else {
                    _visibleSeries.add(i);
                  }
                });
              },
            ),
        ],
      ),
    );
  }
}

class _SearchHeader extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final ValueChanged<String> onSubmit;

  const _SearchHeader({
    required this.controller,
    required this.focusNode,
    required this.onSubmit,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Company',
          style: TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w800,
              fontSize: 26,
              letterSpacing: -0.4),
        ),
        const SizedBox(height: 6),
        const Text('Type a ticker or name. Instant offline analysis.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 13)),
        const SizedBox(height: 16),
        TextField(
          controller: controller,
          focusNode: focusNode,
          textInputAction: TextInputAction.search,
          onSubmitted: (v) {
            if (v.trim().isNotEmpty) onSubmit(v.trim());
          },
          decoration: InputDecoration(
            hintText: 'Search PGR, MET, Travelers, Aflac…',
            prefixIcon: const Icon(Icons.search_rounded,
                color: AppColors.textMuted),
            suffixIcon: controller.text.isNotEmpty
                ? IconButton(
                    icon: const Icon(Icons.close_rounded,
                        color: AppColors.textMuted),
                    onPressed: () => controller.clear(),
                  )
                : null,
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final t in const ['PGR', 'TRV', 'CB', 'MET', 'AFL', 'UNH'])
              _QuickPick(
                label: t,
                onTap: () {
                  controller.text = t;
                  onSubmit(t);
                },
              ),
          ],
        ),
      ],
    ).animate().fadeIn(duration: 300.ms);
  }
}

class _QuickPick extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  const _QuickPick({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
          decoration: BoxDecoration(
            color: AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppColors.border),
          ),
          child: Text(label,
              style: const TextStyle(
                  color: AppColors.accent,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.2,
                  fontSize: 11.5)),
        ),
      ),
    );
  }
}

class _Suggestions extends StatelessWidget {
  final List<Map<String, dynamic>> suggestions;
  final ValueChanged<String> onTap;
  const _Suggestions({required this.suggestions, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          for (var i = 0; i < suggestions.length; i++)
            _SuggestionTile(
              entry: suggestions[i],
              isLast: i == suggestions.length - 1,
              onTap: () => onTap(suggestions[i]['ticker'].toString()),
            )
        ],
      ),
    );
  }
}

class _SuggestionTile extends StatelessWidget {
  final Map<String, dynamic> entry;
  final bool isLast;
  final VoidCallback onTap;
  const _SuggestionTile(
      {required this.entry, required this.isLast, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            border: !isLast
                ? const Border(bottom: BorderSide(color: AppColors.divider))
                : null,
          ),
          child: Row(
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  entry['ticker']?.toString() ?? '',
                  style: const TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 11,
                    letterSpacing: 1.2,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(entry['name']?.toString() ?? '',
                    style: const TextStyle(color: AppColors.textPrimary)),
              ),
              Text(entry['type']?.toString() ?? 'Unknown',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11)),
            ],
          ),
        ),
      ),
    );
  }
}

class _AnalysisBody extends StatelessWidget {
  final CompanyAnalysis analysis;
  final Set<int> visible;
  final ValueChanged<int> onToggleSeries;
  const _AnalysisBody({
    required this.analysis,
    required this.visible,
    required this.onToggleSeries,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Header(analysis: analysis),
        const SizedBox(height: 16),
        if (analysis.kpis.primary.isNotEmpty) ...[
          const SectionHeader(title: 'Headline KPIs'),
          _KpiGrid(kpis: analysis.kpis.primary),
          const SizedBox(height: 24),
        ],
        if (analysis.riskRadar.factors.isNotEmpty) ...[
          const SectionHeader(title: 'Risk Radar'),
          GlassCard(child: RiskRadarChart(radar: analysis.riskRadar))
              .animate()
              .fadeIn(duration: 400.ms),
          const SizedBox(height: 24),
        ],
        if (analysis.series.isNotEmpty) ...[
          const SectionHeader(
            title: '5-Year Trend',
            subtitle: 'Tap a metric to toggle the line',
          ),
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _SeriesLegend(
                    series: analysis.series,
                    visible: visible,
                    onToggle: onToggleSeries),
                const SizedBox(height: 12),
                TrendChart(
                    series: analysis.series, visibleIndices: visible.toList()),
              ],
            ),
          ),
          const SizedBox(height: 24),
        ],
        if (analysis.insights.isNotEmpty) ...[
          const SectionHeader(title: 'Insights'),
          ...List.generate(
            analysis.insights.length,
            (i) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: InsightCard(insight: analysis.insights[i], index: i),
            ),
          ),
          const SizedBox(height: 16),
        ],
        if (analysis.kpis.secondary.isNotEmpty) ...[
          const SectionHeader(title: 'Secondary KPIs'),
          _KpiGrid(kpis: analysis.kpis.secondary),
          const SizedBox(height: 24),
        ],
        if (analysis.rawMetrics.isNotEmpty) ...[
          const SectionHeader(title: 'Latest filing snapshot'),
          _RawMetricsTable(metrics: analysis.rawMetrics),
          const SizedBox(height: 16),
        ],
        Center(
          child: Text(
            'Source: ${analysis.dataSource}',
            style: const TextStyle(color: AppColors.textMuted, fontSize: 11),
          ),
        ),
      ],
    );
  }
}

class _Header extends StatelessWidget {
  final CompanyAnalysis analysis;
  const _Header({required this.analysis});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Row(
        children: [
          if (analysis.score > 0) ...[
            ScoreRing(score: analysis.score),
            const SizedBox(width: 18),
          ],
          Expanded(
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
                        analysis.company.ticker,
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
                        color: AppColors.surfaceElevated,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.border),
                      ),
                      child: Text(
                        analysis.company.insurerType.toUpperCase(),
                        style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.2,
                          fontSize: 10,
                        ),
                      ),
                    ),
                    if (analysis.lastFiscalYear != null) ...[
                      const SizedBox(width: 8),
                      Text('FY${analysis.lastFiscalYear}',
                          style: const TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 11,
                              fontWeight: FontWeight.w600)),
                    ],
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  analysis.company.name,
                  style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontWeight: FontWeight.w700,
                      fontSize: 18,
                      height: 1.2),
                ),
                const SizedBox(height: 8),
                Text(analysis.headline,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12.5,
                        height: 1.45)),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 400.ms).slideY(begin: 0.1);
  }
}

class _KpiGrid extends StatelessWidget {
  final List<Kpi> kpis;
  const _KpiGrid({required this.kpis});

  @override
  Widget build(BuildContext context) {
    if (kpis.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Text('No metrics available.',
            style: TextStyle(color: AppColors.textMuted)),
      );
    }
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: kpis.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.05,
      ),
      itemBuilder: (context, i) => KpiCard(kpi: kpis[i], index: i),
    );
  }
}

class _SeriesLegend extends StatelessWidget {
  final List<FinancialSeries> series;
  final Set<int> visible;
  final ValueChanged<int> onToggle;
  const _SeriesLegend({
    required this.series,
    required this.visible,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 6,
      children: List.generate(series.length, (i) {
        final color =
            AppColors.seriesPalette[i % AppColors.seriesPalette.length];
        final isOn = visible.contains(i);
        return GestureDetector(
          onTap: () => onToggle(i),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: isOn
                  ? color.withValues(alpha: 0.13)
                  : AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                  color: isOn ? color.withValues(alpha: 0.6) : AppColors.border),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration:
                      BoxDecoration(color: color, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(series[i].label,
                    style: TextStyle(
                        color: isOn
                            ? AppColors.textPrimary
                            : AppColors.textMuted,
                        fontWeight: FontWeight.w600,
                        fontSize: 11.5)),
              ],
            ),
          ),
        );
      }),
    );
  }
}

class _RawMetricsTable extends StatelessWidget {
  final Map<String, dynamic> metrics;
  const _RawMetricsTable({required this.metrics});

  @override
  Widget build(BuildContext context) {
    final rows = metrics.entries.where((e) => e.value != null).toList()
      ..sort((a, b) => a.key.compareTo(b.key));
    return GlassCard(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      child: Column(
        children: [
          for (var i = 0; i < rows.length; i++)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                border: i != rows.length - 1
                    ? const Border(
                        bottom: BorderSide(color: AppColors.divider))
                    : null,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      _label(rows[i].key),
                      style: const TextStyle(
                          color: AppColors.textSecondary, fontSize: 12.5),
                    ),
                  ),
                  Text(
                    Formatters.currencyM(_asNum(rows[i].value)),
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  num? _asNum(dynamic v) {
    if (v is num) return v;
    if (v is String) return num.tryParse(v);
    return null;
  }

  String _label(String key) {
    switch (key) {
      case 'premiums':
        return 'Net Premiums Earned';
      case 'losses':
        return 'Losses & Benefits';
      case 'expenses':
        return 'Underwriting Expenses';
      case 'investment_income':
        return 'Investment Income';
      case 'net_income':
        return 'Net Income';
      case 'reserves':
        return 'Policy / Loss Reserves';
      case 'equity':
        return 'Stockholders\' Equity';
      case 'assets':
        return 'Total Assets';
      default:
        return key.replaceAll('_', ' ').toUpperCase();
    }
  }
}
