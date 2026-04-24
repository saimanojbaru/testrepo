import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../state/telegram_controller.dart';

class TelegramSetupScreen extends ConsumerStatefulWidget {
  const TelegramSetupScreen({super.key});

  @override
  ConsumerState<TelegramSetupScreen> createState() =>
      _TelegramSetupScreenState();
}

class _TelegramSetupScreenState extends ConsumerState<TelegramSetupScreen> {
  final _token = TextEditingController();
  final _chat = TextEditingController();

  @override
  void initState() {
    super.initState();
    final c = ref.read(telegramControllerProvider).config;
    if (c != null) {
      _token.text = c.botToken;
      _chat.text = c.chatId;
    }
  }

  @override
  void dispose() {
    _token.dispose();
    _chat.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await ref.read(telegramControllerProvider).save(
          TelegramConfig(
            botToken: _token.text.trim(),
            chatId: _chat.text.trim(),
          ),
        );
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Telegram config saved')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = ref.watch(telegramControllerProvider);
    return Scaffold(
      backgroundColor: const Color(0xFF0B1220),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(
          'Telegram Alerts',
          style: GoogleFonts.jetBrainsMono(fontWeight: FontWeight.w700),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _statusBadge(ctrl),
          const SizedBox(height: 18),
          _step('1', 'CREATE A BOT',
              'Open @BotFather in Telegram, send /newbot, follow the prompts. BotFather will give you a token like 7123456789:AA...'),
          const SizedBox(height: 10),
          _step('2', 'GET YOUR CHAT ID',
              'Send any message to your new bot. Then visit\nhttps://api.telegram.org/bot<TOKEN>/getUpdates and copy the chat.id value.'),
          const SizedBox(height: 16),
          Text('CREDENTIALS',
              style: GoogleFonts.jetBrainsMono(
                  fontSize: 11,
                  letterSpacing: 2,
                  color: const Color(0xFF22D3EE),
                  fontWeight: FontWeight.bold)),
          const SizedBox(height: 6),
          _field(_token, 'Bot token', obscure: true),
          _field(_chat, 'Chat ID', hint: 'e.g. 12345678 or -100... for groups'),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: ctrl.phase == TelegramPhase.saving ? null : _save,
            icon: const Icon(Icons.save),
            label: const Text('SAVE'),
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF22D3EE),
              foregroundColor: Colors.black,
              minimumSize: const Size.fromHeight(44),
            ),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            onPressed: ctrl.configured &&
                    ctrl.phase != TelegramPhase.sending
                ? () => ref
                    .read(telegramControllerProvider)
                    .sendTestMessage()
                : null,
            icon: ctrl.phase == TelegramPhase.sending
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send),
            label: const Text('SEND TEST MESSAGE'),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(44),
              foregroundColor: Colors.white,
            ),
          ),
          if (ctrl.configured) ...[
            const SizedBox(height: 18),
            TextButton.icon(
              onPressed: () => ref.read(telegramControllerProvider).clear(),
              icon: const Icon(Icons.delete, color: Color(0xFFF43F5E)),
              label: const Text('REMOVE CREDENTIALS',
                  style: TextStyle(color: Color(0xFFF43F5E))),
            ),
          ],
          if (ctrl.phase == TelegramPhase.error && ctrl.error != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF43F5E).withOpacity(0.1),
                border: Border.all(color: const Color(0xFFF43F5E)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(ctrl.error!,
                  style: GoogleFonts.jetBrainsMono(
                      fontSize: 11, color: const Color(0xFFF43F5E))),
            ),
          ],
        ],
      ),
    );
  }

  Widget _statusBadge(TelegramController ctrl) {
    final (bg, fg, label, sub) = switch (ctrl.phase) {
      TelegramPhase.sent => (
          const Color(0xFF10B981),
          Colors.black,
          'MESSAGE SENT',
          'Check Telegram — alerts are ready'
        ),
      TelegramPhase.error => (
          const Color(0xFFF43F5E),
          Colors.white,
          'ERROR',
          ctrl.error ?? 'check your credentials'
        ),
      _ when ctrl.configured => (
          const Color(0xFF22D3EE),
          Colors.black,
          'CONFIGURED',
          'Hourly + daily reports will land in your chat'
        ),
      _ => (
          const Color(0xFF334155),
          Colors.white,
          'NOT CONFIGURED',
          'Add a bot below to receive P&L and signal alerts'
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

  Widget _step(String n, String title, String body) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 26,
          height: 26,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: const Color(0xFF22D3EE).withOpacity(0.15),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: const Color(0xFF22D3EE)),
          ),
          child: Text(n,
              style: GoogleFonts.jetBrainsMono(
                  color: const Color(0xFF22D3EE),
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
                      letterSpacing: 1.5,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 2),
              Text(body,
                  style: GoogleFonts.jetBrainsMono(
                      color: Colors.white70, fontSize: 10, height: 1.5)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _field(TextEditingController c, String label,
      {bool obscure = false, String? hint}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        obscureText: obscure,
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
