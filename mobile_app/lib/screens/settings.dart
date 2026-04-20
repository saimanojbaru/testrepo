import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/client.dart';
import '../state/agent_state.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final _urlCtrl = TextEditingController();
  final _secretCtrl = TextEditingController();
  double _dailyLoss = 2000;
  double _kelly = 0.25;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadSaved();
  }

  Future<void> _loadSaved() async {
    final url = await ApiClient.savedBackendUrl();
    if (url != null) _urlCtrl.text = url;
  }

  Future<void> _connect() async {
    setState(() { _saving = true; _error = null; });
    try {
      final token = await ApiClient.instance.login(_urlCtrl.text.trim(), _secretCtrl.text.trim());
      await ref.read(agentStateProvider.notifier).connect(_urlCtrl.text.trim(), token);
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _saving = false);
    }
  }

  Future<void> _saveRisk() async {
    setState(() { _saving = true; _error = null; });
    try {
      await ApiClient.instance.patchRiskConfig({
        'max_daily_loss': _dailyLoss,
        'kelly_max_fraction': _kelly,
      });
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Risk config updated')));
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Settings', style: GoogleFonts.jetBrainsMono())),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Section(title: 'Connection', children: [
            TextField(
              controller: _urlCtrl,
              decoration: const InputDecoration(
                labelText: 'Backend URL',
                hintText: 'http://192.168.1.10:8000',
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _secretCtrl,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Shared secret'),
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _saving ? null : _connect,
              child: const Text('Connect'),
            ),
          ]),
          const SizedBox(height: 16),
          _Section(title: 'Risk Caps', children: [
            Text('Daily Loss Cap: ₹${_dailyLoss.toStringAsFixed(0)}'),
            Slider(
              value: _dailyLoss,
              min: 500,
              max: 10000,
              divisions: 19,
              label: '₹${_dailyLoss.toStringAsFixed(0)}',
              onChanged: (v) => setState(() => _dailyLoss = v),
            ),
            Text('Kelly Fraction: ${(_kelly * 100).toStringAsFixed(0)}%'),
            Slider(
              value: _kelly,
              min: 0.05,
              max: 0.50,
              divisions: 9,
              label: '${(_kelly * 100).toStringAsFixed(0)}%',
              onChanged: (v) => setState(() => _kelly = v),
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: _saving ? null : _saveRisk,
              child: const Text('Save Risk Config'),
            ),
          ]),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(color: Colors.redAccent)),
          ],
        ],
      ),
    );
  }

  @override
  void dispose() {
    _urlCtrl.dispose();
    _secretCtrl.dispose();
    super.dispose();
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children});
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        ...children,
      ],
    );
  }
}
