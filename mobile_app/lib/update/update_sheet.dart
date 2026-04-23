import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import 'update_controller.dart';

class UpdateSheet extends ConsumerWidget {
  const UpdateSheet({super.key});

  static Future<void> maybeShow(BuildContext context, WidgetRef ref) async {
    final ctrl = ref.read(updateControllerProvider);
    if (ctrl.phase == UpdatePhase.available && ctrl.info != null) {
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: const Color(0xFF0B1220),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
        ),
        builder: (_) => const UpdateSheet(),
      );
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ctrl = ref.watch(updateControllerProvider);
    final info = ctrl.info;
    if (info == null && ctrl.phase != UpdatePhase.error) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.system_update,
                  color: Color(0xFF22D3EE), size: 22),
              const SizedBox(width: 8),
              Text('UPDATE AVAILABLE',
                  style: GoogleFonts.jetBrainsMono(
                    color: const Color(0xFF22D3EE),
                    fontSize: 12,
                    letterSpacing: 2,
                    fontWeight: FontWeight.bold,
                  )),
              const Spacer(),
              if (ctrl.phase != UpdatePhase.downloading &&
                  ctrl.phase != UpdatePhase.installing)
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white38),
                  onPressed: () {
                    ctrl.dismiss();
                    Navigator.of(context).pop();
                  },
                ),
            ],
          ),
          const SizedBox(height: 6),
          if (info != null) ...[
            Text(
              info.latestName,
              style: GoogleFonts.jetBrainsMono(
                  color: Colors.white,
                  fontSize: 15,
                  fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 2),
            Text(
              '${info.shortCurrent} → ${info.shortLatest}  ·  ${info.sizeLabel}',
              style: GoogleFonts.jetBrainsMono(
                  color: Colors.white54, fontSize: 11),
            ),
            const SizedBox(height: 12),
            if (info.releaseNotes.isNotEmpty)
              Container(
                constraints: const BoxConstraints(maxHeight: 180),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFF111827),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.white10),
                ),
                child: SingleChildScrollView(
                  child: Text(
                    info.releaseNotes,
                    style: GoogleFonts.jetBrainsMono(
                        color: Colors.white70, fontSize: 11, height: 1.5),
                  ),
                ),
              ),
          ],
          const SizedBox(height: 14),
          if (ctrl.phase == UpdatePhase.downloading)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Downloading… ${(ctrl.progress * 100).toStringAsFixed(0)}%',
                  style: GoogleFonts.jetBrainsMono(
                      color: Colors.white, fontSize: 11),
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: ctrl.progress,
                    minHeight: 6,
                    backgroundColor: Colors.white10,
                    valueColor: const AlwaysStoppedAnimation(
                        Color(0xFF22D3EE)),
                  ),
                ),
              ],
            )
          else if (ctrl.phase == UpdatePhase.installing)
            Text('Opening Android installer…',
                style: GoogleFonts.jetBrainsMono(
                    color: Colors.white, fontSize: 11))
          else if (ctrl.phase == UpdatePhase.error)
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF43F5E).withOpacity(0.1),
                border: Border.all(color: const Color(0xFFF43F5E)),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                ctrl.error ?? 'Something went wrong.',
                style: GoogleFonts.jetBrainsMono(
                    color: const Color(0xFFF43F5E), fontSize: 11),
              ),
            )
          else
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () {
                      ctrl.dismiss();
                      Navigator.of(context).pop();
                    },
                    child: const Text('LATER'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF22D3EE),
                      foregroundColor: Colors.black,
                    ),
                    onPressed: () => ctrl.downloadAndInstall(),
                    icon: const Icon(Icons.download),
                    label: const Text('UPDATE'),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}
