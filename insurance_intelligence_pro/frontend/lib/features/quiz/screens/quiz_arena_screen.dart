import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../state/quiz_state.dart';
import 'quiz_play_screen.dart';

/// Category selector for the Quiz Arena.
class QuizArenaScreen extends StatelessWidget {
  const QuizArenaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Quiz Arena',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
      ),
      body: AnimatedBuilder(
        animation: QuizState.instance,
        builder: (context, _) {
          final cats = QuizState.instance.categories;
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
            children: [
              _HeroBanner(),
              const SizedBox(height: 18),
              for (var i = 0; i < cats.length; i++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _CategoryTile(category: cats[i], index: i),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _HeroBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(14),
            ),
            child: const Icon(Icons.flash_on_rounded,
                color: Colors.white, size: 24),
          ),
          const SizedBox(width: 14),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Audit Duel',
                    style: TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 16)),
                SizedBox(height: 4),
                Text(
                    '5-question solo rounds with explanations tied to SSAP / ASC / AS standards. Win streaks unlock badges and XP.',
                    style: TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                        height: 1.4)),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn();
  }
}

class _CategoryTile extends StatelessWidget {
  final QuizCategory category;
  final int index;
  const _CategoryTile({required this.category, required this.index});

  IconData _icon() {
    switch (category.icon) {
      case 'shield':
        return Icons.shield_outlined;
      case 'warehouse':
        return Icons.warehouse_rounded;
      case 'swap':
        return Icons.swap_horiz_rounded;
      case 'trend':
        return Icons.trending_up_rounded;
      case 'scale':
        return Icons.balance_rounded;
      case 'check':
        return Icons.fact_check_rounded;
      case 'bolt':
        return Icons.bolt_rounded;
      case 'book':
      default:
        return Icons.menu_book_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final qs = QuizState.instance;
    final high = qs.highScore(category.key);
    final streak = qs.streak(category.key);
    final count = qs.questionsFor(category.key).length;
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => QuizPlayScreen(categoryKey: category.key))),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(_icon(), color: Colors.white, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(category.label,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 14)),
                const SizedBox(height: 2),
                Text(category.description,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 11.5,
                        height: 1.35)),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Text('$count questions',
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                    const SizedBox(width: 8),
                    if (high > 0)
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.accent.withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text('High $high/5',
                            style: const TextStyle(
                                color: AppColors.accent,
                                fontSize: 10,
                                fontWeight: FontWeight.w800)),
                      ),
                    if (streak > 1) ...[
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.warning.withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          children: [
                            const Icon(
                                Icons.local_fire_department_rounded,
                                color: AppColors.warning,
                                size: 11),
                            const SizedBox(width: 2),
                            Text('$streak',
                                style: const TextStyle(
                                    color: AppColors.warning,
                                    fontSize: 10,
                                    fontWeight: FontWeight.w800)),
                          ],
                        ),
                      )
                    ]
                  ],
                ),
              ],
            ),
          ),
          const Icon(Icons.arrow_forward_ios_rounded,
              color: AppColors.accent, size: 12),
        ],
      ),
    ).animate().fadeIn(duration: 240.ms, delay: (24 * index).ms);
  }
}
