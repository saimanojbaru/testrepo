import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../state/agent_state.dart';

class PositionsScreen extends ConsumerWidget {
  const PositionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positions = ref.watch(agentStateProvider).positions;

    return Scaffold(
      appBar: AppBar(title: Text('Open Positions', style: GoogleFonts.jetBrainsMono())),
      body: positions.isEmpty
          ? const Center(child: Text('No open positions'))
          : ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: positions.length,
              itemBuilder: (ctx, i) => _PositionTile(pos: positions[i]),
            ),
    );
  }
}

class _PositionTile extends StatelessWidget {
  const _PositionTile({required this.pos});
  final Map<String, dynamic> pos;

  @override
  Widget build(BuildContext context) {
    final qty = (pos['quantity'] as int?) ?? 0;
    final uPnl = (pos['unrealized_pnl'] as num?)?.toDouble() ?? 0.0;
    final pnlColor = uPnl >= 0 ? Colors.greenAccent : Colors.redAccent;
    final direction = qty > 0 ? 'LONG' : 'SHORT';
    final directionColor = qty > 0 ? Colors.greenAccent : Colors.redAccent;

    return Card(
      child: ListTile(
        title: Text(
          pos['instrument_key']?.toString().split('|').last ?? '–',
          style: GoogleFonts.jetBrainsMono(fontWeight: FontWeight.bold),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Avg: ₹${(pos['avg_price'] as num?)?.toStringAsFixed(2) ?? '–'}'
                '  LTP: ₹${(pos['last_price'] as num?)?.toStringAsFixed(2) ?? '–'}'),
            Chip(
              label: Text('$direction  ×${qty.abs()}',
                  style: const TextStyle(fontSize: 11)),
              backgroundColor: directionColor.withOpacity(0.15),
              side: BorderSide(color: directionColor, width: 0.5),
              padding: EdgeInsets.zero,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ],
        ),
        trailing: Text(
          '₹${uPnl.toStringAsFixed(2)}',
          style: TextStyle(
            color: pnlColor,
            fontWeight: FontWeight.bold,
            fontFamily: 'monospace',
          ),
        ),
      ),
    );
  }
}
