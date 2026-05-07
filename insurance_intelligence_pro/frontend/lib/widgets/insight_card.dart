import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../theme/app_colors.dart';
import 'glass_card.dart';

class InsightCard extends StatelessWidget {
  final Insight insight;
  final int index;

  const InsightCard({super.key, required this.insight, this.index = 0});

  Color _color() {
    switch (insight.level) {
      case 'positive':
        return AppColors.positive;
      case 'warning':
        return AppColors.warning;
      case 'negative':
        return AppColors.negative;
      default:
        return AppColors.accent;
    }
  }

  IconData _icon() {
    switch (insight.icon) {
      case 'shield':
        return Icons.shield_outlined;
      case 'trend-up':
        return Icons.trending_up_rounded;
      case 'trend-down':
        return Icons.trending_down_rounded;
      case 'rocket':
        return Icons.rocket_launch_outlined;
      case 'coins':
        return Icons.stacked_bar_chart_rounded;
      case 'medical':
        return Icons.medical_services_outlined;
      case 'vault':
        return Icons.account_balance_outlined;
      case 'alert':
        return Icons.warning_amber_rounded;
      case 'warning':
        return Icons.error_outline;
      case 'balance':
        return Icons.balance_rounded;
      case 'globe':
        return Icons.public;
      case 'standard':
        return Icons.menu_book_outlined;
      case 'info':
        return Icons.info_outline;
      default:
        return Icons.auto_awesome_outlined;
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _color();
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(16, 16, 18, 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: color.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: color.withOpacity(0.45)),
            ),
            child: Icon(_icon(), color: color, size: 20),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  insight.title,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    fontSize: 14.5,
                    height: 1.25,
                  ),
                ),
                if ((insight.detail ?? '').isNotEmpty) ...[
                  const SizedBox(height: 6),
                  Text(
                    insight.detail!,
                    style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 12.5,
                      height: 1.45,
                    ),
                  ),
                ],
                if (insight.tags.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: insight.tags
                        .map((t) => Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: AppColors.surfaceHigh,
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(color: AppColors.border),
                              ),
                              child: Text(
                                t.toUpperCase(),
                                style: TextStyle(
                                  color: AppColors.textMuted,
                                  fontSize: 9.5,
                                  letterSpacing: 0.6,
                                ),
                              ),
                            ))
                        .toList(),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    )
        .animate()
        .fadeIn(duration: 400.ms, delay: (80 * index).ms)
        .slideX(begin: 0.06, curve: Curves.easeOutCubic);
  }
}
