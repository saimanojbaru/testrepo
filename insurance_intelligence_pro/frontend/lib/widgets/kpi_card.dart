import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/kpi.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import 'glass_card.dart';

class KpiCard extends StatelessWidget {
  final Kpi kpi;
  final int index;
  final VoidCallback? onTap;

  const KpiCard(
      {super.key, required this.kpi, this.index = 0, this.onTap});

  @override
  Widget build(BuildContext context) {
    final statusColor = AppColors.statusColor(kpi.status);
    final isPercent = kpi.unit == '%';
    return GlassCard(
      padding: const EdgeInsets.all(16),
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: statusColor,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: statusColor.withValues(alpha: 0.45),
                      blurRadius: 10,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  kpi.label,
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                    letterSpacing: 0.4,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              _DirectionGlyph(direction: kpi.direction, color: statusColor),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                kpi.value == null
                    ? '–'
                    : (isPercent
                        ? Formatters.pct(kpi.value)
                        : Formatters.num1(kpi.value)),
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 26,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.5,
                ),
              ),
              if (!isPercent) ...[
                const SizedBox(width: 4),
                Text(
                  kpi.unit,
                  style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 12,
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              if (kpi.deltaYoy != null) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: statusColor.withValues(alpha: 0.4)),
                  ),
                  child: Text(
                    'YoY ${Formatters.signedPct(kpi.deltaYoy)}',
                    style: TextStyle(
                      color: statusColor,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
              ],
              if (kpi.benchmark != null)
                Text(
                  'Bench ${Formatters.pct(kpi.benchmark)}',
                  style:
                      TextStyle(color: AppColors.textMuted, fontSize: 11),
                ),
            ],
          ),
          if (kpi.description != null) ...[
            const SizedBox(height: 10),
            Text(
              kpi.description!,
              style: TextStyle(color: AppColors.textMuted, fontSize: 11.5, height: 1.4),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          if (kpi.source != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.accent.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(
                    color: AppColors.accent.withValues(alpha: 0.30)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.description_outlined,
                      color: AppColors.accent, size: 11),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      kpi.source!.displayLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.4),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (onTap != null) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                const Icon(Icons.tune_rounded,
                    color: AppColors.textMuted, size: 11),
                const SizedBox(width: 4),
                const Expanded(
                  child: Text(
                    'Tap · formula · source · peer rank',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.2),
                  ),
                ),
                const Icon(Icons.arrow_forward_ios_rounded,
                    color: AppColors.accent, size: 10),
              ],
            ),
          ],
        ],
      ),
    )
        .animate()
        .fadeIn(duration: 400.ms, delay: (60 * index).ms)
        .slideY(begin: 0.08, curve: Curves.easeOutCubic);
  }
}

class _DirectionGlyph extends StatelessWidget {
  final String direction;
  final Color color;
  const _DirectionGlyph({required this.direction, required this.color});

  @override
  Widget build(BuildContext context) {
    final IconData icon;
    switch (direction) {
      case 'up':
        icon = Icons.arrow_upward_rounded;
        break;
      case 'down':
        icon = Icons.arrow_downward_rounded;
        break;
      default:
        icon = Icons.remove_rounded;
    }
    return Icon(icon, size: 16, color: color);
  }
}
