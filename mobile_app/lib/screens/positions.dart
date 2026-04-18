import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../state/agent_state.dart';

class PositionsScreen extends ConsumerWidget {
  const PositionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positions = ref.watch(agentProvider).snapshot.positions;
    if (positions.isEmpty) {
      return const Center(child: Text('No open positions'));
    }
    final currency = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 2);
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: positions.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, i) {
        final p = positions[i];
        final pnlColor = p.unrealizedPnl >= 0 ? Colors.greenAccent : Colors.redAccent;
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(p.symbol, style: Theme.of(context).textTheme.titleMedium),
                    Text(
                      currency.format(p.unrealizedPnl),
                      style: TextStyle(fontWeight: FontWeight.bold, color: pnlColor),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 16,
                  runSpacing: 6,
                  children: [
                    _kv('Qty', p.quantity.toString()),
                    _kv('Avg', currency.format(p.averagePrice)),
                    _kv('LTP', currency.format(p.lastPrice)),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _kv(String k, String v) => Text('$k: $v', style: const TextStyle(color: Colors.white70));
}
