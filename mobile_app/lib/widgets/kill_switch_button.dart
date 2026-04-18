import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/agent_state.dart';

/// Large hold-to-trigger button. Avoids accidental taps.
class KillSwitchButton extends ConsumerStatefulWidget {
  const KillSwitchButton({super.key});

  @override
  ConsumerState<KillSwitchButton> createState() => _KillSwitchButtonState();
}

class _KillSwitchButtonState extends ConsumerState<KillSwitchButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  );
  bool _fired = false;
  String? _status;

  @override
  void initState() {
    super.initState();
    _ctrl.addStatusListener((s) {
      if (s == AnimationStatus.completed && !_fired) {
        _fired = true;
        _trigger();
      }
    });
  }

  Future<void> _trigger() async {
    setState(() => _status = 'Triggering...');
    try {
      await ref.read(agentProvider.notifier).triggerKillSwitch();
      setState(() => _status = 'Kill switch activated.');
    } catch (e) {
      setState(() => _status = 'Failed: $e');
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final killActive = ref.watch(agentProvider).snapshot.risk.killSwitchActive;
    if (killActive) {
      return Column(
        children: [
          FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: Colors.orange,
              minimumSize: const Size.fromHeight(64),
            ),
            onPressed: () async {
              try {
                await ref.read(agentProvider.notifier).clearKillSwitch();
                setState(() => _status = 'Kill switch cleared.');
              } catch (e) {
                setState(() => _status = 'Failed: $e');
              }
            },
            icon: const Icon(Icons.refresh),
            label: const Text('Clear kill switch'),
          ),
          if (_status != null) Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_status!),
          ),
        ],
      );
    }

    return Column(
      children: [
        GestureDetector(
          onTapDown: (_) {
            _fired = false;
            _ctrl.forward(from: 0);
          },
          onTapUp: (_) => _ctrl.reverse(),
          onTapCancel: () => _ctrl.reverse(),
          child: AnimatedBuilder(
            animation: _ctrl,
            builder: (context, child) {
              return Container(
                height: 80,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                    stops: [_ctrl.value, _ctrl.value],
                    colors: const [Colors.redAccent, Color(0xFF3B0B0B)],
                  ),
                  borderRadius: BorderRadius.circular(40),
                  boxShadow: const [BoxShadow(color: Colors.redAccent, blurRadius: 14)],
                ),
                alignment: Alignment.center,
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.block, size: 28),
                    SizedBox(width: 12),
                    Text(
                      'HOLD TO KILL',
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, letterSpacing: 2),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Hold for 1.5 seconds to square off all positions and halt trading.',
          style: Theme.of(context).textTheme.bodySmall,
          textAlign: TextAlign.center,
        ),
        if (_status != null) Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(_status!),
        ),
      ],
    );
  }
}
