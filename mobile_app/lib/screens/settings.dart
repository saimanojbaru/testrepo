import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/agent_state.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  double? _dailyLoss;
  double? _capital;
  int? _maxOpen;
  double? _kelly;
  bool _busy = false;
  String? _status;

  @override
  Widget build(BuildContext context) {
    final risk = ref.watch(agentProvider).snapshot.risk;
    _dailyLoss ??= risk.dailyLossCap;
    _capital ??= risk.tradingCapital;
    _maxOpen ??= 3;
    _kelly ??= 0.25;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _slider(
          label: 'Daily loss cap',
          value: _dailyLoss!,
          min: 500,
          max: 20000,
          suffix: '₹',
          onChanged: (v) => setState(() => _dailyLoss = v),
        ),
        _slider(
          label: 'Trading capital',
          value: _capital!,
          min: 25000,
          max: 1000000,
          suffix: '₹',
          onChanged: (v) => setState(() => _capital = v),
        ),
        _slider(
          label: 'Max open positions',
          value: _maxOpen!.toDouble(),
          min: 1,
          max: 10,
          divisions: 9,
          onChanged: (v) => setState(() => _maxOpen = v.round()),
        ),
        _slider(
          label: 'Kelly fraction',
          value: _kelly!,
          min: 0.05,
          max: 1.0,
          divisions: 19,
          onChanged: (v) => setState(() => _kelly = v),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _busy ? null : _save,
          icon: const Icon(Icons.save),
          label: const Text('Apply'),
        ),
        if (_status != null) Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Text(_status!),
        ),
      ],
    );
  }

  Widget _slider({
    required String label,
    required double value,
    required double min,
    required double max,
    int? divisions,
    String suffix = '',
    required ValueChanged<double> onChanged,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(label),
                Text('$suffix${value.toStringAsFixed(suffix.isEmpty ? 2 : 0)}'),
              ],
            ),
            Slider(
              value: value.clamp(min, max),
              min: min,
              max: max,
              divisions: divisions,
              onChanged: onChanged,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _status = null;
    });
    try {
      await ref.read(agentProvider.notifier).patchRiskConfig({
        'trading_capital': _capital,
        'max_loss_per_day': _dailyLoss,
        'max_open_positions': _maxOpen,
        'kelly_fraction': _kelly,
      });
      setState(() => _status = 'Risk config applied.');
    } catch (e) {
      setState(() => _status = 'Failed: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
