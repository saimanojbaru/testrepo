import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../state/agent_state.dart';
import '../widgets/kill_switch_button.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(agentProvider);
    final snap = state.snapshot;
    final risk = snap.risk;
    final cap = risk.dailyLossCap <= 0 ? 1.0 : risk.dailyLossCap;
    final drawdownPct = ((-risk.dailyPnl) / cap).clamp(0.0, 1.0);
    final pnlColor = risk.dailyPnl >= 0 ? Colors.greenAccent : Colors.redAccent;
    final currency = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 0);

    return RefreshIndicator(
      onRefresh: () async {},
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _PnlCard(pnl: risk.dailyPnl, color: pnlColor, currency: currency, paperMode: snap.paperMode, symbol: snap.symbol),
          const SizedBox(height: 16),
          _DailyLossCard(drawdownPct: drawdownPct, cap: risk.dailyLossCap, currency: currency),
          const SizedBox(height: 16),
          _SparklineCard(points: snap.pnlCurve),
          const SizedBox(height: 16),
          _StatRow(
            items: [
              ('Trades', risk.tradesToday.toString()),
              ('Open', risk.openPositions.toString()),
              ('Capital', currency.format(risk.tradingCapital)),
            ],
          ),
          const SizedBox(height: 16),
          if (risk.halted || risk.killSwitchActive)
            _HaltBanner(reason: risk.haltReason.isEmpty ? 'Kill switch active' : risk.haltReason),
          const SizedBox(height: 24),
          const KillSwitchButton(),
        ],
      ),
    );
  }
}

class _PnlCard extends StatelessWidget {
  const _PnlCard({required this.pnl, required this.color, required this.currency, required this.paperMode, required this.symbol});
  final double pnl;
  final Color color;
  final NumberFormat currency;
  final bool paperMode;
  final String symbol;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Today\'s P&L', style: Theme.of(context).textTheme.labelLarge),
                Chip(
                  label: Text(paperMode ? 'PAPER' : 'LIVE'),
                  backgroundColor: paperMode ? Colors.blueGrey : Colors.orange,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              currency.format(pnl),
              style: TextStyle(fontSize: 44, fontWeight: FontWeight.w700, color: color),
            ),
            const SizedBox(height: 4),
            Text('Symbol: $symbol', style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class _DailyLossCard extends StatelessWidget {
  const _DailyLossCard({required this.drawdownPct, required this.cap, required this.currency});
  final double drawdownPct;
  final double cap;
  final NumberFormat currency;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Daily loss cap', style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 8),
            LinearProgressIndicator(
              value: drawdownPct,
              minHeight: 10,
              borderRadius: BorderRadius.circular(8),
              color: drawdownPct > 0.7 ? Colors.redAccent : Colors.tealAccent,
            ),
            const SizedBox(height: 8),
            Text('${(drawdownPct * 100).toStringAsFixed(0)}% used · cap ${currency.format(cap)}'),
          ],
        ),
      ),
    );
  }
}

class _SparklineCard extends StatelessWidget {
  const _SparklineCard({required this.points});
  final List<double> points;

  @override
  Widget build(BuildContext context) {
    if (points.isEmpty) {
      return Card(
        child: SizedBox(
          height: 180,
          child: Center(
            child: Text('Waiting for P&L data...', style: Theme.of(context).textTheme.bodyMedium),
          ),
        ),
      );
    }
    final spots = [
      for (int i = 0; i < points.length; i++) FlSpot(i.toDouble(), points[i]),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: SizedBox(
          height: 180,
          child: LineChart(
            LineChartData(
              gridData: const FlGridData(show: false),
              titlesData: const FlTitlesData(show: false),
              borderData: FlBorderData(show: false),
              lineBarsData: [
                LineChartBarData(
                  spots: spots,
                  isCurved: true,
                  barWidth: 3,
                  color: Colors.tealAccent,
                  dotData: const FlDotData(show: false),
                  belowBarData: BarAreaData(show: true, color: Colors.tealAccent.withOpacity(0.15)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _StatRow extends StatelessWidget {
  const _StatRow({required this.items});
  final List<(String, String)> items;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: items
          .map((it) => Expanded(
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Column(
                      children: [
                        Text(it.$1, style: Theme.of(context).textTheme.labelMedium),
                        const SizedBox(height: 6),
                        Text(it.$2, style: Theme.of(context).textTheme.titleMedium),
                      ],
                    ),
                  ),
                ),
              ))
          .toList(),
    );
  }
}

class _HaltBanner extends StatelessWidget {
  const _HaltBanner({required this.reason});
  final String reason;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.red.withOpacity(0.15),
        border: Border.all(color: Colors.redAccent),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.warning_amber_rounded, color: Colors.redAccent),
          const SizedBox(width: 8),
          Expanded(child: Text(reason, style: const TextStyle(color: Colors.redAccent))),
        ],
      ),
    );
  }
}
