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
          final zumble =
              cats.where((c) => c.tier == 'Adaptive').toList();
          final tiers = ['Beginner', 'Intermediate', 'Advanced', 'Expert'];
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
            children: [
              _HeroBanner(),
              const SizedBox(height: 18),
              // Zumble Mode hero
              if (zumble.isNotEmpty)
                _ZumbleHero(category: zumble.first),
              const SizedBox(height: 18),
              for (final tier in tiers) ...[
                _TierHeader(tier: tier),
                const SizedBox(height: 8),
                for (final c
                    in cats.where((c) => c.tier == tier).toList().asMap().entries)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child:
                        _CategoryTile(category: c.value, index: c.key),
                  ),
                const SizedBox(height: 12),
              ],
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

class _TierHeader extends StatelessWidget {
  final String tier;
  const _TierHeader({required this.tier});

  Color _tierColor() {
    switch (tier) {
      case 'Beginner':
        return AppColors.positive;
      case 'Intermediate':
        return AppColors.accent;
      case 'Advanced':
        return AppColors.warning;
      case 'Expert':
      default:
        return AppColors.negative;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = _tierColor();
    return Row(
      children: [
        Container(
          width: 4,
          height: 18,
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(2),
            boxShadow: AppColors.glow(AppColors.accent, radius: 6),
          ),
        ),
        const SizedBox(width: 10),
        Text(tier.toUpperCase(),
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 13,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4)),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
          decoration: BoxDecoration(
            color: c.withValues(alpha: 0.18),
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: c.withValues(alpha: 0.5)),
          ),
          child: const Icon(Icons.flash_on_rounded, size: 11, color: Colors.white),
        ),
      ],
    );
  }
}

class _ZumbleHero extends StatelessWidget {
  final QuizCategory category;
  const _ZumbleHero({required this.category});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        boxShadow: AppColors.glow(AppColors.violet, radius: 26),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: () => Navigator.of(context).push(MaterialPageRoute(
              builder: (_) =>
                  QuizPlayScreen(categoryKey: category.key))),
          child: Container(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                        color: Colors.white.withValues(alpha: 0.4)),
                  ),
                  child: const Icon(Icons.bolt_rounded,
                      color: Colors.white, size: 28),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('ZUMBLE MODE',
                          style: TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w900,
                              fontSize: 11,
                              letterSpacing: 2.0)),
                      const SizedBox(height: 4),
                      const Text('Adaptive · Mixed Difficulty',
                          style: TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                              fontSize: 17,
                              height: 1.2)),
                      const SizedBox(height: 4),
                      Text(category.description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              color: Colors.white
                                  .withValues(alpha: 0.85),
                              fontSize: 11.5,
                              height: 1.4)),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.25),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.play_arrow_rounded,
                      color: Colors.white, size: 22),
                ),
              ],
            ),
          ),
        ),
      ),
    ).animate(onPlay: (c) => c.repeat(reverse: true))
        .shimmer(duration: 2200.ms, color: Colors.white.withValues(alpha: 0.18));
  }
}
