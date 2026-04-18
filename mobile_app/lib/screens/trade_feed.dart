import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../api/models.dart';
import '../state/agent_state.dart';

class TradeFeedScreen extends ConsumerWidget {
  const TradeFeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final events = ref.watch(agentProvider).snapshot.events;
    if (events.isEmpty) {
      return const Center(child: Text('No trades yet'));
    }
    final time = DateFormat('HH:mm:ss');
    return ListView.separated(
      padding: const EdgeInsets.all(12),
      itemCount: events.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final e = events[i];
        return ListTile(
          leading: _iconFor(e.kind),
          title: Text(e.message),
          subtitle: Text([
            time.format(e.timestamp.toLocal()),
            if (e.strategy != null) e.strategy!,
            if (e.regime != null) 'regime ${e.regime}',
          ].join(' · ')),
          trailing: e.pnl != null
              ? Text(
                  '₹${e.pnl!.toStringAsFixed(0)}',
                  style: TextStyle(
                    color: e.pnl! >= 0 ? Colors.greenAccent : Colors.redAccent,
                    fontWeight: FontWeight.bold,
                  ),
                )
              : null,
        );
      },
    );
  }

  Icon _iconFor(String kind) {
    switch (kind) {
      case 'fill':
        return const Icon(Icons.play_arrow, color: Colors.tealAccent);
      case 'exit':
        return const Icon(Icons.stop, color: Colors.amberAccent);
      case 'kill_switch':
        return const Icon(Icons.dangerous, color: Colors.redAccent);
      case 'regime':
        return const Icon(Icons.swap_horiz, color: Colors.purpleAccent);
      case 'risk':
        return const Icon(Icons.warning, color: Colors.orangeAccent);
      default:
        return const Icon(Icons.circle, size: 10);
    }
  }
}
