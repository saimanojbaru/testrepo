import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../state/app_mode.dart';

class ModeSwitch extends ConsumerWidget {
  const ModeSwitch({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(appModeProvider);
    return GestureDetector(
      onTap: () => _showConfirm(context, ref, mode),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(
          color: const Color(0xFF0F172A),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: mode.isLive
                ? const Color(0xFFF43F5E)
                : const Color(0xFF10B981),
            width: 1.2,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _Pill(label: 'PAPER', active: mode.isPaper, color: const Color(0xFF10B981)),
            _Pill(label: 'LIVE', active: mode.isLive, color: const Color(0xFFF43F5E)),
          ],
        ),
      ),
    );
  }

  void _showConfirm(BuildContext ctx, WidgetRef ref, AppMode current) {
    if (current.isPaper) {
      showDialog<void>(
        context: ctx,
        builder: (dCtx) => AlertDialog(
          backgroundColor: const Color(0xFF111827),
          title: Text('Switch to LIVE?',
              style: GoogleFonts.jetBrainsMono(color: Colors.white)),
          content: Text(
            'Live mode places REAL orders with REAL money through your connected broker. Paper positions are not carried over.',
            style: GoogleFonts.jetBrainsMono(
                fontSize: 12, color: Colors.white70),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dCtx),
                child: const Text('Cancel')),
            FilledButton(
              style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFFF43F5E)),
              onPressed: () {
                ref.read(appModeProvider.notifier).set(AppMode.live);
                Navigator.pop(dCtx);
              },
              child: const Text('Go LIVE'),
            ),
          ],
        ),
      );
    } else {
      ref.read(appModeProvider.notifier).set(AppMode.paper);
    }
  }
}

class _Pill extends StatelessWidget {
  const _Pill({required this.label, required this.active, required this.color});
  final String label;
  final bool active;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: active ? color : Colors.transparent,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: GoogleFonts.jetBrainsMono(
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 2,
          color: active ? Colors.black : Colors.white70,
        ),
      ),
    );
  }
}
