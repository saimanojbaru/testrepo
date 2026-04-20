import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../state/agent_state.dart';
import '../widgets/kill_switch_button.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(agentStateProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('Scalping Agent', style: GoogleFonts.jetBrainsMono()),
        actions: [
          _ConnectionChip(connected: state.isConnected),
          const SizedBox(width: 8),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _PnlCard(state: state),
          const SizedBox(height: 12),
          _PnlSparkline(state: state),
          const SizedBox(height: 12),
          _StatsRow(state: state),
          const SizedBox(height: 24),
          KillSwitchButton(engaged: state.killSwitchEngaged),
        ],
      ),
    );
  }
}

class _ConnectionChip extends StatelessWidget {
  const _ConnectionChip({required this.connected});
  final bool connected;

  @override
  Widget build(BuildContext context) {
    return Chip(
      label: Text(connected ? 'LIVE' : 'OFFLINE',
          style: const TextStyle(fontSize: 11)),
      avatar: CircleAvatar(
        backgroundColor: connected ? Colors.greenAccent : Colors.redAccent,
        radius: 5,
      ),
      padding: EdgeInsets.zero,
      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
    );
  }
}

class _PnlCard extends StatelessWidget {
  const _PnlCard({required this.state});
  final AgentState state;

  @override
  Widget build(BuildContext context) {
    final pnl = state.dailyPnl;
    final color = pnl >= 0 ? Colors.greenAccent : Colors.redAccent;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Today\'s P&L', style: Theme.of(context).textTheme.labelMedium),
            const SizedBox(height: 8),
            Text(
              '₹${pnl.toStringAsFixed(2)}',
              style: GoogleFonts.jetBrainsMono(
                fontSize: 36,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
            const SizedBox(height: 4),
            LinearProgressIndicator(
              value: (state.dailyPnl.abs() / (state.capital * 0.02)).clamp(0, 1),
              backgroundColor: Theme.of(context).colorScheme.surfaceVariant,
              color: state.dailyPnl < -state.capital * 0.01
                  ? Colors.redAccent
                  : Colors.greenAccent,
            ),
            const SizedBox(height: 4),
            Text(
              'Daily loss cap: ₹${(state.capital * 0.02).toStringAsFixed(0)}',
              style: Theme.of(context).textTheme.labelSmall,
            ),
            if (state.regime != null) ...[
              const SizedBox(height: 8),
              Chip(
                label: Text('Regime: ${state.regime}',
                    style: const TextStyle(fontSize: 11)),
                padding: EdgeInsets.zero,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _PnlSparkline extends StatelessWidget {
  const _PnlSparkline({required this.state});
  final AgentState state;

  @override
  Widget build(BuildContext context) {
    final events = state.tradeEvents
        .where((e) => e.containsKey('net_pnl'))
        .take(30)
        .toList()
        .reversed
        .toList();
    if (events.isEmpty) {
      return const SizedBox.shrink();
    }
    double cumPnl = 0;
    final spots = <FlSpot>[];
    for (int i = 0; i < events.length; i++) {
      cumPnl += (events[i]['net_pnl'] as num?)?.toDouble() ?? 0;
      spots.add(FlSpot(i.toDouble(), cumPnl));
    }
    final color = cumPnl >= 0 ? Colors.greenAccent : Colors.redAccent;

    return SizedBox(
      height: 100,
      child: LineChart(LineChartData(
        gridData: const FlGridData(show: false),
        titlesData: const FlTitlesData(show: false),
        borderData: FlBorderData(show: false),
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            color: color,
            barWidth: 2,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              color: color.withOpacity(0.1),
            ),
          ),
        ],
      )),
    );
  }
}

class _StatsRow extends StatelessWidget {
  const _StatsRow({required this.state});
  final AgentState state;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _Stat('Open Pos', '${state.openPositions}'),
        _Stat('Trades', '${state.tradesToday}'),
        _Stat('Unrealized', '₹${state.unrealizedPnl.toStringAsFixed(0)}'),
        _Stat('Realized', '₹${state.realizedPnl.toStringAsFixed(0)}'),
      ].map((s) => Expanded(child: s)).toList(),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Column(
          children: [
            Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
            Text(label,
                style: Theme.of(context).textTheme.labelSmall,
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}
