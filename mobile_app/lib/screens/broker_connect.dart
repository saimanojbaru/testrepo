import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';

import '../brokers/upstox/upstox_auth.dart';
import '../state/broker_controller.dart';
import '../state/market_controller.dart';

class BrokerConnectScreen extends ConsumerStatefulWidget {
  const BrokerConnectScreen({super.key});

  @override
  ConsumerState<BrokerConnectScreen> createState() =>
      _BrokerConnectScreenState();
}

class _BrokerConnectScreenState extends ConsumerState<BrokerConnectScreen> {
  final _apiKey = TextEditingController();
  final _apiSecret = TextEditingController();
  final _redirect = TextEditingController();
  final _code = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final c = ref.read(brokerControllerProvider).credentials;
    if (c != null) {
      _apiKey.text = c.apiKey;
      _apiSecret.text = c.apiSecret;
      _redirect.text = c.redirectUri;
    }
  }

  @override
  void dispose() {
    _apiKey.dispose();
    _apiSecret.dispose();
    _redirect.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _saveCreds() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(brokerControllerProvider).saveCredentials(
            UpstoxCredentials(
              apiKey: _apiKey.text.trim(),
              apiSecret: _apiSecret.text.trim(),
              redirectUri: _redirect.text.trim(),
            ),
          );
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _saving = false);
    }
  }

  Future<void> _openAuth() async {
    await _saveCreds();
    final bc = ref.read(brokerControllerProvider);
    if (bc.credentials == null || !bc.credentials!.isComplete) {
      setState(() => _error = 'Fill API key, secret, and redirect URI first.');
      return;
    }
    final url = bc.authorizationUrl();
    final launched =
        await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!launched) {
      setState(() => _error = 'Failed to open browser.');
    }
  }

  Future<void> _exchange() async {
    final bc = ref.read(brokerControllerProvider);
    final mc = ref.read(marketControllerProvider);
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await _saveCreds();
      await bc.exchangeCode(_code.text.trim());
      final client = bc.client;
      if (client != null) {
        mc.useLiveUpstox(client);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Upstox connected — live feed on')),
        );
        Navigator.of(context).pop();
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bc = ref.watch(brokerControllerProvider);
    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text('Connect Upstox',
            style: GoogleFonts.jetBrainsMono(fontWeight: FontWeight.w700)),
        actions: [
          IconButton(
            tooltip: 'How to get API credentials',
            icon: const Icon(Icons.help_outline),
            onPressed: () => _showHowTo(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _StatusBanner(status: bc.status, session: bc.session),
          const SizedBox(height: 16),
          _stepHeader('1.  CREDENTIALS'),
          Text(
            'Register an app on Upstox Developer Console and paste its credentials here.',
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white54),
          ),
          const SizedBox(height: 10),
          _field(_apiKey, 'API Key (client_id)'),
          _field(_apiSecret, 'API Secret', obscure: true),
          _field(_redirect, 'Redirect URI',
              hint: 'Must match the URI on Upstox dashboard'),
          const SizedBox(height: 18),
          _stepHeader('2.  AUTHORIZE'),
          Text(
            'Tap below — Upstox login opens in your browser. After you sign in, your browser redirects to your redirect URI with ?code=XXX. Copy that code and paste it in step 3.',
            style: GoogleFonts.jetBrainsMono(
                fontSize: 11, color: Colors.white54),
          ),
          const SizedBox(height: 10),
          FilledButton.icon(
            onPressed: _saving ? null : _openAuth,
            icon: const Icon(Icons.open_in_browser),
            label: const Text('OPEN UPSTOX LOGIN'),
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF22D3EE),
              foregroundColor: Colors.black,
              minimumSize: const Size.fromHeight(44),
            ),
          ),
          const SizedBox(height: 18),
          _stepHeader('3.  PASTE CODE'),
          _field(_code, 'Authorization code',
              hint: 'From the redirected URL ?code=...'),
          const SizedBox(height: 10),
          FilledButton.icon(
            onPressed: _saving || _code.text.isEmpty ? null : _exchange,
            icon: _saving
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: Colors.black),
                  )
                : const Icon(Icons.link),
            label: const Text('EXCHANGE & CONNECT'),
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF10B981),
              foregroundColor: Colors.black,
              minimumSize: const Size.fromHeight(44),
            ),
          ),
          if (bc.status == BrokerStatus.connected) ...[
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: () async {
                final navigator = Navigator.of(context);
                await ref.read(brokerControllerProvider).disconnect();
                ref.read(marketControllerProvider).revertToSimulated();
                if (mounted) navigator.pop();
              },
              icon: const Icon(Icons.link_off, color: Color(0xFFF43F5E)),
              label: const Text('DISCONNECT',
                  style: TextStyle(color: Color(0xFFF43F5E))),
            ),
          ],
          if (_error != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF43F5E).withOpacity(0.1),
                border: Border.all(color: const Color(0xFFF43F5E)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                _error!,
                style: GoogleFonts.jetBrainsMono(
                    fontSize: 11, color: const Color(0xFFF43F5E)),
              ),
            ),
          ],
        ],
      ),
    );
  }

  void _showHowTo(BuildContext ctx) {
    showModalBottomSheet<void>(
      context: ctx,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF0B1220),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => const _HowToSheet(),
    );
  }

  Widget _stepHeader(String text) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          text,
          style: GoogleFonts.jetBrainsMono(
            color: const Color(0xFF22D3EE),
            fontSize: 11,
            letterSpacing: 2,
            fontWeight: FontWeight.bold,
          ),
        ),
      );

  Widget _field(TextEditingController c, String label,
      {bool obscure = false, String? hint}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        obscureText: obscure,
        onChanged: (_) => setState(() {}),
        style: GoogleFonts.jetBrainsMono(fontSize: 13),
        decoration: InputDecoration(
          labelText: label,
          hintText: hint,
          labelStyle: GoogleFonts.jetBrainsMono(fontSize: 11),
          hintStyle: GoogleFonts.jetBrainsMono(
              fontSize: 10, color: Colors.white38),
          filled: true,
          fillColor: const Color(0xFF111827),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.status, required this.session});
  final BrokerStatus status;
  final UpstoxSession? session;

  @override
  Widget build(BuildContext context) {
    final (bg, fg, label, sub) = switch (status) {
      BrokerStatus.connected => (
          const Color(0xFF10B981),
          Colors.black,
          'CONNECTED',
          session != null
              ? 'Token issued ${_age(session!.issuedAt)} ago · expires at 03:30 IST'
              : '',
        ),
      BrokerStatus.connecting => (
          const Color(0xFF22D3EE),
          Colors.black,
          'CONNECTING…',
          'Exchanging authorization code',
        ),
      BrokerStatus.error => (
          const Color(0xFFF43F5E),
          Colors.white,
          'RECONNECT NEEDED',
          'Token expired or exchange failed',
        ),
      _ => (
          const Color(0xFF334155),
          Colors.white,
          'DISCONNECTED',
          'Running on simulated feed',
        ),
    };
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: GoogleFonts.jetBrainsMono(
                  color: fg,
                  fontSize: 12,
                  letterSpacing: 2,
                  fontWeight: FontWeight.bold)),
          const SizedBox(height: 2),
          Text(sub,
              style: GoogleFonts.jetBrainsMono(color: fg, fontSize: 10)),
        ],
      ),
    );
  }

  static String _age(DateTime t) {
    final d = DateTime.now().difference(t);
    if (d.inHours > 0) return '${d.inHours}h ${d.inMinutes % 60}m';
    return '${d.inMinutes}m';
  }
}

class _HowToSheet extends StatelessWidget {
  const _HowToSheet();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 18,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.bolt, color: Color(0xFF22D3EE)),
              const SizedBox(width: 8),
              Text('UPSTOX SETUP — 5 MINUTES',
                  style: GoogleFonts.jetBrainsMono(
                    color: const Color(0xFF22D3EE),
                    fontSize: 12,
                    letterSpacing: 2,
                    fontWeight: FontWeight.bold,
                  )),
            ],
          ),
          const SizedBox(height: 14),
          _howStep(
            '1',
            'Open Upstox Developer Console',
            'Go to https://account.upstox.com/developer/apps in a browser on your PC or phone.',
          ),
          _howStep(
            '2',
            'Create a new app',
            'Click "New App". App name: anything (e.g. Scalping Agent). App type: Production.',
          ),
          _howStep(
            '3',
            'Redirect URI',
            'Enter a dummy HTTPS URL you control — e.g. https://localhost/callback. The page will not actually load; you only need Upstox to redirect to it so you can copy the code from the URL.',
          ),
          _howStep(
            '4',
            'Copy API Key + Secret',
            'Once the app is created, Upstox shows you the API Key and API Secret. Keep this tab open.',
          ),
          _howStep(
            '5',
            'Back in this app',
            'Close this sheet. Paste the API Key, Secret, and the exact Redirect URI into the form.',
          ),
          _howStep(
            '6',
            'Tap OPEN UPSTOX LOGIN',
            'Your browser opens. Sign in. Upstox then tries to send you to your redirect URI — the browser will show "unable to reach" or a blank page — this is expected.',
          ),
          _howStep(
            '7',
            'Copy the code from the URL bar',
            'The address bar will look like https://localhost/callback?code=XXX...  Select and copy the XXX... part (everything after code= up to the next & if present).',
          ),
          _howStep(
            '8',
            'Paste and Exchange',
            'Paste into the "Authorization code" box, tap EXCHANGE & CONNECT. Done — live Nifty / BankNifty / FinNifty / Sensex prices start streaming.',
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFF59E0B).withOpacity(0.12),
              border: Border.all(color: const Color(0xFFF59E0B)),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              'Note · Upstox access tokens expire daily at 03:30 AM IST. You\'ll need to repeat steps 6–8 each morning (credentials stay saved).',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 10, color: Colors.white, height: 1.5),
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(44),
            ),
            child: const Text('GOT IT'),
          ),
        ],
      ),
    );
  }

  Widget _howStep(String n, String title, String body) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 22,
            height: 22,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: const Color(0xFF22D3EE).withOpacity(0.18),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: const Color(0xFF22D3EE)),
            ),
            child: Text(n,
                style: GoogleFonts.jetBrainsMono(
                    color: const Color(0xFF22D3EE),
                    fontSize: 11,
                    fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: GoogleFonts.jetBrainsMono(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.bold)),
                const SizedBox(height: 2),
                Text(body,
                    style: GoogleFonts.jetBrainsMono(
                        color: Colors.white70,
                        fontSize: 10,
                        height: 1.45)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
