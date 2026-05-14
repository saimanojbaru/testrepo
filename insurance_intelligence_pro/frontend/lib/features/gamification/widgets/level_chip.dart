import 'package:flutter/material.dart';

import '../../../theme/app_colors.dart';
import '../services/xp_service.dart';

/// Compact level/XP chip used in the drawer header and elsewhere.
/// Only renders when Fun Mode is on.
class LevelChip extends StatelessWidget {
  final bool dense;
  const LevelChip({super.key, this.dense = false});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: XpService.instance,
      builder: (context, _) {
        final svc = XpService.instance;
        if (!svc.funMode) return const SizedBox.shrink();
        final lvl = svc.currentLevel;
        return Container(
          padding: EdgeInsets.symmetric(
              horizontal: dense ? 8 : 10, vertical: dense ? 4 : 6),
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.star_rounded, color: Colors.white, size: 12),
              const SizedBox(width: 4),
              Text(
                'L${lvl.level} · ${svc.xp} XP',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: dense ? 10 : 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.6,
                ),
              ),
              if (svc.streakDays > 1) ...[
                const SizedBox(width: 6),
                const Icon(Icons.local_fire_department_rounded,
                    color: Colors.white, size: 12),
                const SizedBox(width: 2),
                Text(
                  '${svc.streakDays}d',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: dense ? 10 : 11,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ]
            ],
          ),
        );
      },
    );
  }
}
