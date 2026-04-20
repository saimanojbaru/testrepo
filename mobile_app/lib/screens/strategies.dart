import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../state/market_controller.dart';
import '../strategies/base.dart';

class StrategiesScreen extends ConsumerWidget {
  const StrategiesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mc = ref.watch(marketControllerProvider);
    final all = mc.runner.strategies;
    final active = all.where((s) => s.enabled).length;

    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Text(
          'Strategies',
          style: GoogleFonts.jetBrainsMono(
            fontWeight: FontWeight.w700,
            fontSize: 18,
          ),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 14),
            child: Center(
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF111827),
                  border: Border.all(
                    color: active > 0
                        ? const Color(0xFF22D3EE)
                        : Colors.white12,
                  ),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  '$active / ${all.length} ACTIVE',
                  style: GoogleFonts.jetBrainsMono(
                    fontSize: 10,
                    letterSpacing: 1.5,
                    color: active > 0
                        ? const Color(0xFF22D3EE)
                        : Colors.white54,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 4, 14, 24),
        children: [
          _Banner(autoExecute: mc.autoExecuteSignals, onToggle: mc.setAutoExecute),
          const SizedBox(height: 12),
          for (final s in all) _StrategyCard(strategy: s),
          const SizedBox(height: 20),
          _Legend(),
        ],
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({required this.autoExecute, required this.onToggle});
  final bool autoExecute;
  final ValueChanged<bool> onToggle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [
          Color(0xFF0EA5E9),
          Color(0xFF22D3EE),
        ]),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          const Icon(Icons.auto_graph, color: Colors.black),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'AUTO-EXECUTE',
                  style: GoogleFonts.jetBrainsMono(
                    fontSize: 11,
                    letterSpacing: 2,
                    fontWeight: FontWeight.bold,
                    color: Colors.black,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  autoExecute
                      ? 'Signals place paper orders automatically.'
                      : 'Signals appear in feed — you place manually.'
                  ,
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 10, color: Colors.black87),
                ),
              ],
            ),
          ),
          Switch(
            value: autoExecute,
            onChanged: onToggle,
            activeColor: Colors.black,
            activeTrackColor: Colors.white,
          ),
        ],
      ),
    );
  }
}

class _StrategyCard extends StatefulWidget {
  const _StrategyCard({required this.strategy});
  final Strategy strategy;

  @override
  State<_StrategyCard> createState() => _StrategyCardState();
}

class _StrategyCardState extends State<_StrategyCard> {
  @override
  void initState() {
    super.initState();
    widget.strategy.addListener(_onUpdate);
  }

  @override
  void dispose() {
    widget.strategy.removeListener(_onUpdate);
    super.dispose();
  }

  void _onUpdate() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.strategy;
    final regimeColor = _regimeColor(s.regime);
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF111827),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: s.enabled ? regimeColor.withOpacity(0.55) : Colors.white10,
          width: s.enabled ? 1.3 : 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: regimeColor.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: regimeColor.withOpacity(0.45)),
                  ),
                  alignment: Alignment.center,
                  child: Icon(_regimeIcon(s.regime),
                      color: regimeColor, size: 18),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        s.name,
                        style: GoogleFonts.jetBrainsMono(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        s.tagline,
                        style: GoogleFonts.jetBrainsMono(
                          color: Colors.white60,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: s.enabled,
                  onChanged: (v) => s.setEnabled(v),
                  activeColor: regimeColor,
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                _Chip(label: s.regime, color: regimeColor),
                const SizedBox(width: 6),
                _Chip(
                  label: s.enabled ? 'ARMED' : 'IDLE',
                  color: s.enabled
                      ? const Color(0xFF10B981)
                      : Colors.white24,
                ),
                const Spacer(),
                _Stat(label: 'SIG', value: '${s.signalsToday}'),
                _Stat(
                  label: 'WIN%',
                  value: (s.winRate * 100).toStringAsFixed(0),
                  color: s.winRate >= 0.5
                      ? const Color(0xFF10B981)
                      : const Color(0xFFF43F5E),
                ),
                _Stat(
                  label: 'PNL',
                  value: '₹${s.pnl.toStringAsFixed(0)}',
                  color: s.pnl >= 0
                      ? const Color(0xFF10B981)
                      : const Color(0xFFF43F5E),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Color _regimeColor(String r) {
    switch (r) {
      case 'TRENDING':
        return const Color(0xFF22D3EE);
      case 'RANGING':
        return const Color(0xFFA78BFA);
      case 'VOLATILE':
        return const Color(0xFFF59E0B);
    }
    return Colors.white54;
  }

  IconData _regimeIcon(String r) {
    switch (r) {
      case 'TRENDING':
        return Icons.show_chart;
      case 'RANGING':
        return Icons.waves;
      case 'VOLATILE':
        return Icons.bolt;
    }
    return Icons.auto_graph;
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withOpacity(0.4)),
      ),
      child: Text(
        label,
        style: GoogleFonts.jetBrainsMono(
          color: color,
          fontSize: 9,
          letterSpacing: 1.4,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value, this.color});
  final String label;
  final String value;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            label,
            style: GoogleFonts.jetBrainsMono(
              color: Colors.white38,
              fontSize: 8,
              letterSpacing: 1.2,
            ),
          ),
          Text(
            value,
            style: GoogleFonts.jetBrainsMono(
              color: color ?? Colors.white,
              fontSize: 11,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'REGIMES',
            style: GoogleFonts.jetBrainsMono(
              color: Colors.white54,
              fontSize: 10,
              letterSpacing: 2,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          const _LegendRow(
            color: Color(0xFF22D3EE),
            label: 'TRENDING',
            text: 'EMA crosses, breakouts — ride directional moves.',
          ),
          const _LegendRow(
            color: Color(0xFFA78BFA),
            label: 'RANGING',
            text: 'VWAP fades, RSI extremes — revert to mean.',
          ),
          const _LegendRow(
            color: Color(0xFFF59E0B),
            label: 'VOLATILE',
            text: 'Bollinger squeezes — catch expansion.',
          ),
        ],
      ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  const _LegendRow(
      {required this.color, required this.label, required this.text});
  final Color color;
  final String label;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            margin: const EdgeInsets.only(top: 4),
            width: 8,
            height: 8,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 78,
            child: Text(
              label,
              style: GoogleFonts.jetBrainsMono(
                color: color,
                fontSize: 10,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
          ),
          Expanded(
            child: Text(
              text,
              style: GoogleFonts.jetBrainsMono(
                  color: Colors.white70, fontSize: 10),
            ),
          ),
        ],
      ),
    );
  }
}
