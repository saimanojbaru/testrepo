import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

class CompareScreen extends StatefulWidget {
  const CompareScreen({super.key});

  @override
  State<CompareScreen> createState() => _CompareScreenState();
}

class _CompareScreenState extends State<CompareScreen> {
  final TextEditingController _input = TextEditingController();
  final List<String> _selected = ['PGR', 'TRV', 'CB'];
  late Map<String, dynamic> _result;

  @override
  void initState() {
    super.initState();
    _result = AnalyticsService.instance.compare(_selected);
  }

  void _runCompare() =>
      setState(() => _result = AnalyticsService.instance.compare(_selected));

  void _autoPeers(String anchor) {
    final r = AnalyticsService.instance.autoCompare(anchor);
    final companies = ((r['companies'] as List?) ?? [])
        .map<String>((c) => (c['company']?['ticker'] ?? '').toString())
        .where((t) => t.isNotEmpty)
        .toList();
    setState(() {
      _result = r;
      _selected
        ..clear()
        ..addAll(companies);
    });
  }

  void _addTicker(String t) {
    final upper = t.trim().toUpperCase();
    if (upper.isEmpty || _selected.contains(upper) || _selected.length >= 6) {
      return;
    }
    final resolved = AnalyticsService.instance.resolveCompany(upper);
    if (resolved == null) return;
    setState(() => _selected.add(resolved['ticker'].toString()));
    _input.clear();
    _runCompare();
  }

  void _removeTicker(String t) {
    if (_selected.length <= 1) return;
    setState(() => _selected.remove(t));
    _runCompare();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 120),
        children: [
          const Text(
            'Compare',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 26,
                letterSpacing: -0.4),
          ),
          const SizedBox(height: 6),
          const Text(
            'Pick a peer set or add tickers — ranked on metrics that matter.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 13),
          ),
          const SizedBox(height: 14),
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final t in _selected)
                      _SelectedChip(
                          label: t, onRemove: () => _removeTicker(t)),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _input,
                        textInputAction: TextInputAction.done,
                        textCapitalization: TextCapitalization.characters,
                        onSubmitted: _addTicker,
                        decoration: const InputDecoration(
                          hintText: 'Add ticker (e.g. ALL)',
                          isDense: true,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    GestureDetector(
                      onTap: () => _addTicker(_input.text),
                      child: Container(
                        height: 48,
                        width: 48,
                        decoration: BoxDecoration(
                          gradient: AppColors.primaryGradient,
                          borderRadius: BorderRadius.circular(14),
                          boxShadow: [
                            BoxShadow(
                              color: AppColors.primary.withValues(alpha: 0.4),
                              blurRadius: 16,
                              offset: const Offset(0, 6),
                            ),
                          ],
                        ),
                        child: const Icon(Icons.add_rounded,
                            color: Colors.white, size: 22),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text('AUTO PEER SETS',
                    style: TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 10,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final pre in const [
                      ['PGR', 'P&C'],
                      ['MET', 'Life'],
                      ['CB', 'P&C'],
                      ['AFL', 'Life'],
                      ['UNH', 'Health']
                    ])
                      _PeerSetButton(
                        ticker: pre[0],
                        type: pre[1],
                        onTap: () => _autoPeers(pre[0]),
                      ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          _ResultBody(result: _result),
        ],
      ),
    );
  }
}

class _SelectedChip extends StatelessWidget {
  final String label;
  final VoidCallback onRemove;
  const _SelectedChip({required this.label, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        gradient: AppColors.primaryGradient,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label,
              style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.2,
                  fontSize: 11.5)),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: onRemove,
            child: const Icon(Icons.close_rounded,
                size: 14, color: Colors.white),
          ),
        ],
      ),
    );
  }
}

class _PeerSetButton extends StatelessWidget {
  final String ticker;
  final String type;
  final VoidCallback onTap;
  const _PeerSetButton(
      {required this.ticker, required this.type, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 6,
                height: 6,
                decoration: const BoxDecoration(
                    color: AppColors.accent, shape: BoxShape.circle),
              ),
              const SizedBox(width: 6),
              Text(ticker,
                  style: const TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w700,
                      fontSize: 11,
                      letterSpacing: 1.2)),
              const SizedBox(width: 6),
              Text(type,
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 10.5)),
            ],
          ),
        ),
      ),
    );
  }
}

class _ResultBody extends StatelessWidget {
  final Map<String, dynamic> result;
  const _ResultBody({required this.result});

  @override
  Widget build(BuildContext context) {
    final companies = ((result['companies'] as List?) ?? [])
        .cast<Map<String, dynamic>>();
    final metrics =
        ((result['metrics'] as List?) ?? []).cast<Map<String, dynamic>>();
    final verdict = result['verdict']?.toString() ?? '';
    final type = result['insurer_type']?.toString() ?? '';

    if (companies.isEmpty) {
      return GlassCard(
        child: const Text('Add at least two tickers to compare.',
            style: TextStyle(color: AppColors.textMuted)),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(
          title: 'Smart Verdict',
          subtitle: 'Peer group: $type',
        ),
        GlassCard(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF18284A), Color(0xFF0B1426)],
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0xFF1F4FFF), Color(0xFF00E0C7)],
                  ),
                ),
                child: const Icon(Icons.emoji_events_rounded,
                    color: Colors.white, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(verdict,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                        height: 1.4)),
              ),
            ],
          ),
        ).animate().fadeIn(duration: 400.ms),
        const SizedBox(height: 18),
        const SectionHeader(title: 'Composite Scores'),
        _ScoreList(companies: companies),
        const SizedBox(height: 18),
        const SectionHeader(title: 'Metric-by-metric'),
        _MetricsTable(metrics: metrics, companies: companies),
      ],
    );
  }
}

class _ScoreList extends StatelessWidget {
  final List<Map<String, dynamic>> companies;
  const _ScoreList({required this.companies});

  @override
  Widget build(BuildContext context) {
    final sorted = [...companies]
      ..sort((a, b) => (b['score'] as num).compareTo(a['score'] as num));
    final topScore = (sorted.first['score'] as num).toDouble();
    return GlassCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      child: Column(
        children: List.generate(sorted.length, (i) {
          final c = sorted[i];
          final company = c['company'] as Map;
          final score = (c['score'] as num).toDouble();
          final pct = topScore == 0 ? 0.0 : score / topScore;
          final color = score >= 75
              ? AppColors.positive
              : score >= 60
                  ? AppColors.accent
                  : score >= 45
                      ? AppColors.warning
                      : AppColors.negative;
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                SizedBox(
                  width: 22,
                  child: Text('#${i + 1}',
                      style: const TextStyle(
                          color: AppColors.textMuted,
                          fontWeight: FontWeight.w700,
                          fontSize: 11)),
                ),
                SizedBox(
                  width: 50,
                  child: Text(company['ticker']?.toString() ?? '',
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.2)),
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(company['name']?.toString() ?? '',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w600,
                              fontSize: 13)),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(6),
                        child: LinearProgressIndicator(
                          value: pct,
                          minHeight: 6,
                          backgroundColor: AppColors.surfaceHigh,
                          valueColor: AlwaysStoppedAnimation(color),
                        ).animate().fadeIn(
                            duration: 350.ms, delay: (60 * i).ms),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Text(score.toStringAsFixed(0),
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w800,
                        fontSize: 18)),
              ],
            ),
          );
        }),
      ),
    );
  }
}

class _MetricsTable extends StatelessWidget {
  final List<Map<String, dynamic>> metrics;
  final List<Map<String, dynamic>> companies;
  const _MetricsTable({required this.metrics, required this.companies});

  @override
  Widget build(BuildContext context) {
    if (metrics.isEmpty) return const SizedBox();
    final tickers = companies
        .map<String>((c) => (c['company']?['ticker'] ?? '').toString())
        .toList();
    return GlassCard(
      padding: EdgeInsets.zero,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: ConstrainedBox(
          constraints: BoxConstraints(
              minWidth: MediaQuery.of(context).size.width - 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: const BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
                ),
                child: Row(
                  children: [
                    const SizedBox(
                      width: 140,
                      child: Text('METRIC',
                          style: TextStyle(
                              color: AppColors.textMuted,
                              fontWeight: FontWeight.w700,
                              fontSize: 11,
                              letterSpacing: 1.4)),
                    ),
                    for (final t in tickers)
                      SizedBox(
                        width: 80,
                        child: Text(t,
                            textAlign: TextAlign.right,
                            style: const TextStyle(
                                color: AppColors.accent,
                                fontWeight: FontWeight.w700,
                                fontSize: 11,
                                letterSpacing: 1.2)),
                      ),
                  ],
                ),
              ),
              for (var i = 0; i < metrics.length; i++)
                _MetricRow(
                    metric: metrics[i],
                    tickers: tickers,
                    isLast: i == metrics.length - 1),
            ],
          ),
        ),
      ),
    );
  }
}

class _MetricRow extends StatelessWidget {
  final Map<String, dynamic> metric;
  final List<String> tickers;
  final bool isLast;
  const _MetricRow({
    required this.metric,
    required this.tickers,
    required this.isLast,
  });

  @override
  Widget build(BuildContext context) {
    final values = ((metric['values'] as List?) ?? []).cast<Map<String, dynamic>>();
    final byTicker = {for (final v in values) v['ticker'].toString(): v};
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        border: !isLast
            ? const Border(bottom: BorderSide(color: AppColors.divider))
            : null,
      ),
      child: Row(
        children: [
          SizedBox(
            width: 140,
            child: Text(metric['label']?.toString() ?? '',
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    fontSize: 12.5)),
          ),
          for (final t in tickers)
            SizedBox(
              width: 80,
              child: _ValueCell(
                value: byTicker[t]?['value'] as num?,
                rank: byTicker[t]?['rank'] as int?,
              ),
            ),
        ],
      ),
    );
  }
}

class _ValueCell extends StatelessWidget {
  final num? value;
  final int? rank;
  const _ValueCell({required this.value, required this.rank});

  @override
  Widget build(BuildContext context) {
    if (value == null) {
      return const Text('–',
          textAlign: TextAlign.right,
          style: TextStyle(color: AppColors.textMuted));
    }
    final isBest = rank == 1;
    final color = isBest ? AppColors.positive : AppColors.textPrimary;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(Formatters.pct(value),
            style: TextStyle(
                color: color, fontWeight: FontWeight.w700, fontSize: 13)),
        if (rank != null)
          Text('Rank ${rank.toString()}',
              style: TextStyle(
                  color: isBest ? AppColors.positive : AppColors.textMuted,
                  fontSize: 9.5,
                  fontWeight: FontWeight.w700)),
      ],
    );
  }
}
