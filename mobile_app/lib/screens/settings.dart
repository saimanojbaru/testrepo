import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../api/client.dart';
import '../state/agent_state.dart';
import '../state/broker_controller.dart';
import '../update/update_controller.dart';
import '../update/update_service.dart';
import '../update/update_sheet.dart';
import 'broker_connect.dart';

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
          _UpstoxTile(),
          const SizedBox(height: 12),
          _UpdateTile(),
          const SizedBox(height: 16),
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

class _UpstoxTile extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bc = ref.watch(brokerControllerProvider);
    final connected = bc.isConnected;
    final color =
        connected ? const Color(0xFF10B981) : const Color(0xFF334155);
    final label = switch (bc.status) {
      BrokerStatus.connected => 'CONNECTED',
      BrokerStatus.connecting => 'CONNECTING',
      BrokerStatus.error => 'RECONNECT',
      _ => 'DISCONNECTED',
    };
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const BrokerConnectScreen()),
      ),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF111827),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: color.withOpacity(0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(Icons.link, color: color),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('UPSTOX LIVE FEED',
                      style: GoogleFonts.jetBrainsMono(
                          fontSize: 12,
                          letterSpacing: 1.5,
                          fontWeight: FontWeight.bold)),
                  const SizedBox(height: 2),
                  Text(
                    connected
                        ? 'Streaming Nifty / BankNifty / FinNifty / Sensex'
                        : 'Tap to authorize — live prices will replace simulated feed',
                    style: GoogleFonts.jetBrainsMono(
                        fontSize: 10, color: Colors.white70),
                  ),
                ],
              ),
            ),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(label,
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 9,
                      letterSpacing: 1.2,
                      fontWeight: FontWeight.bold,
                      color: connected ? Colors.black : Colors.white)),
            ),
          ],
        ),
      ),
    );
  }
}

class _UpdateTile extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ctrl = ref.watch(updateControllerProvider);
    final busy = ctrl.phase == UpdatePhase.checking ||
        ctrl.phase == UpdatePhase.downloading ||
        ctrl.phase == UpdatePhase.installing;
    final hasUpdate = ctrl.phase == UpdatePhase.available;
    final border = hasUpdate
        ? const Color(0xFF22D3EE)
        : const Color(0xFF334155);
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: busy
          ? null
          : () async {
              if (hasUpdate) {
                await UpdateSheet.maybeShow(context, ref);
              } else {
                await ctrl.check();
                if (context.mounted &&
                    ctrl.phase == UpdatePhase.available) {
                  await UpdateSheet.maybeShow(context, ref);
                } else if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('You are up to date.')),
                  );
                }
              }
            },
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF111827),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: border),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: border.withOpacity(0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(
                hasUpdate ? Icons.new_releases : Icons.system_update,
                color: border,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('APP UPDATE',
                      style: GoogleFonts.jetBrainsMono(
                          fontSize: 12,
                          letterSpacing: 1.5,
                          fontWeight: FontWeight.bold)),
                  const SizedBox(height: 2),
                  Text(
                    hasUpdate
                        ? 'New build ${ctrl.info?.shortLatest ?? ''} ready to install'
                        : 'Current build ${UpdateService.buildSha.length >= 7 ? UpdateService.buildSha.substring(0, 7) : UpdateService.buildSha} — tap to check GitHub releases',
                    style: GoogleFonts.jetBrainsMono(
                        fontSize: 10, color: Colors.white70),
                  ),
                ],
              ),
            ),
            if (busy)
              const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            else
              Icon(
                hasUpdate ? Icons.download : Icons.chevron_right,
                color: border,
              ),
          ],
        ),
      ),
    );
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
