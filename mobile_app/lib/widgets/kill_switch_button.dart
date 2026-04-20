import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/client.dart';
import '../state/agent_state.dart';

class KillSwitchButton extends ConsumerStatefulWidget {
  const KillSwitchButton({super.key, required this.engaged});
  final bool engaged;

  @override
  ConsumerState<KillSwitchButton> createState() => _KillSwitchButtonState();
}

class _KillSwitchButtonState extends ConsumerState<KillSwitchButton> {
  bool _holding = false;
  double _holdProgress = 0;
  bool _busy = false;

  static const _holdDuration = Duration(milliseconds: 1500);

  void _onLongPressStart(LongPressStartDetails _) {
    setState(() { _holding = true; _holdProgress = 0; });
    _animateHold();
  }

  void _animateHold() async {
    const steps = 30;
    for (int i = 1; i <= steps; i++) {
      await Future<void>.delayed(Duration(milliseconds: _holdDuration.inMilliseconds ~/ steps));
      if (!_holding || !mounted) return;
      setState(() => _holdProgress = i / steps);
    }
    if (_holding && mounted) _confirm();
  }

  void _onLongPressEnd(LongPressEndDetails _) {
    setState(() { _holding = false; _holdProgress = 0; });
  }

  Future<void> _confirm() async {
    setState(() { _holding = false; _holdProgress = 0; _busy = true; });
    try {
      await ApiClient.instance.triggerKillSwitch(engage: !widget.engaged);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.engaged ? Colors.green : Colors.redAccent;
    final label = widget.engaged ? 'RESUME TRADING' : 'KILL SWITCH';
    final hint = widget.engaged ? 'Hold to re-enable trading' : 'Hold 1.5s to halt all trades';

    return Column(
      children: [
        Text(hint, style: Theme.of(context).textTheme.labelSmall),
        const SizedBox(height: 8),
        GestureDetector(
          onLongPressStart: _busy ? null : _onLongPressStart,
          onLongPressEnd: _busy ? null : _onLongPressEnd,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            height: 80,
            width: double.infinity,
            decoration: BoxDecoration(
              color: color.withOpacity(_holding ? 0.5 : 0.15),
              border: Border.all(color: color, width: 2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (_holding)
                  Positioned.fill(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(10),
                      child: LinearProgressIndicator(
                        value: _holdProgress,
                        backgroundColor: Colors.transparent,
                        color: color.withOpacity(0.3),
                        minHeight: double.infinity,
                      ),
                    ),
                  ),
                if (_busy)
                  CircularProgressIndicator(color: color, strokeWidth: 2)
                else
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        widget.engaged ? Icons.play_arrow : Icons.stop,
                        color: color,
                        size: 28,
                      ),
                      const SizedBox(width: 10),
                      Text(
                        label,
                        style: TextStyle(
                          color: color,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1.5,
                        ),
                      ),
                    ],
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
