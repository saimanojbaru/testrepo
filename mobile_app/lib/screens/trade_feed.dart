import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../state/agent_state.dart';

class TradeFeedScreen extends ConsumerWidget {
  const TradeFeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final events = ref.watch(agentStateProvider).tradeEvents;

    return Scaffold(
      appBar: AppBar(title: Text('Trade Feed', style: GoogleFonts.jetBrainsMono())),
      body: events.isEmpty
          ? const Center(child: Text('Waiting for events…'))
          : ListView.builder(
              padding: const EdgeInsets.all(8),
              itemCount: events.length,
              itemBuilder: (ctx, i) => _EventTile(event: events[i]),
            ),
    );
  }
}

class _EventTile extends StatelessWidget {
  const _EventTile({required this.event});
  final Map<String, dynamic> event;

  @override
  Widget build(BuildContext context) {
    final kind = event['kind']?.toString() ?? event['direction']?.toString() ?? '?';
    final ts = event['ts']?.toString() ?? event['entry_ts']?.toString() ?? '';
    final netPnl = (event['net_pnl'] as num?)?.toDouble();

    Color kindColor = Colors.blueGrey;
    if (kind.contains('fill') || kind.contains('close')) kindColor = Colors.tealAccent;
    if (kind.contains('signal')) kindColor = Colors.amberAccent;
    if (kind.contains('risk') || kind.contains('kill')) kindColor = Colors.redAccent;
    if (kind.contains('regime')) kindColor = Colors.purpleAccent;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 3),
      child: ListTile(
        dense: true,
        leading: Chip(
          label: Text(kind.toUpperCase().replaceAll('_', ' '),
              style: const TextStyle(fontSize: 9)),
          backgroundColor: kindColor.withOpacity(0.15),
          side: BorderSide(color: kindColor, width: 0.5),
          padding: EdgeInsets.zero,
          materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
        title: Text(
          _summary(event),
          style: GoogleFonts.jetBrainsMono(fontSize: 12),
        ),
        subtitle: ts.isNotEmpty
            ? Text(_formatTs(ts), style: const TextStyle(fontSize: 10))
            : null,
        trailing: netPnl != null
            ? Text(
                '₹${netPnl.toStringAsFixed(2)}',
                style: TextStyle(
                  color: netPnl >= 0 ? Colors.greenAccent : Colors.redAccent,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              )
            : null,
      ),
    );
  }

  String _summary(Map<String, dynamic> e) {
    final inst = e['instrument_key']?.toString().split('|').last ??
        e['symbol']?.toString() ??
        '–';
    final dir = e['direction']?.toString() ?? '';
    return '$inst  $dir'.trim();
  }

  String _formatTs(String raw) {
    try {
      final dt = DateTime.parse(raw);
      return DateFormat('HH:mm:ss').format(dt.toLocal());
    } catch (_) {
      return raw.length > 8 ? raw.substring(0, 8) : raw;
    }
  }
}
