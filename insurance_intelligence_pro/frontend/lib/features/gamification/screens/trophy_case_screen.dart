import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/section_header.dart';
import '../services/xp_service.dart';

/// Trophy Case — shows the user's level progress, daily streak, and a
/// gallery of earned + locked badges.
class TrophyCaseScreen extends StatelessWidget {
  const TrophyCaseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Trophy Case',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
      ),
      body: AnimatedBuilder(
        animation: XpService.instance,
        builder: (context, _) {
          final svc = XpService.instance;
          final earned = svc.badges
              .where((b) => svc.earnedBadgeIds.contains(b.id))
              .toList();
          final locked = svc.badges
              .where((b) => !svc.earnedBadgeIds.contains(b.id))
              .toList();
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
            children: [
              _LevelHero(),
              const SizedBox(height: 18),
              SectionHeader(
                title: 'Earned',
                subtitle:
                    '${earned.length} of ${svc.badges.length} badges unlocked',
              ),
              if (earned.isEmpty)
                GlassCard(
                  child: const Text(
                    'No badges yet — complete a checklist item or finish a quiz to start unlocking.',
                    style: TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 13,
                        height: 1.45),
                  ),
                )
              else
                _BadgeGrid(badges: earned, earned: true),
              const SizedBox(height: 18),
              const SectionHeader(
                title: 'Coming up',
                subtitle: 'Badges still locked',
              ),
              _BadgeGrid(badges: locked, earned: false),
            ],
          );
        },
      ),
    );
  }
}

class _LevelHero extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final svc = XpService.instance;
    final pct = svc.progressToNextLevel;
    final next = svc.nextLevel;
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(14),
                ),
                alignment: Alignment.center,
                child: Text('L${svc.currentLevel.level}',
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 18,
                        letterSpacing: 0.4)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(svc.currentLevel.title,
                        style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.w800,
                            fontSize: 18,
                            letterSpacing: -0.3)),
                    const SizedBox(height: 4),
                    Text(
                      next == null
                          ? '${svc.xp} XP · Max level reached'
                          : '${svc.xp} XP · ${next.minXp - svc.xp} XP to ${next.title}',
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 12),
                    ),
                  ],
                ),
              ),
              if (svc.streakDays > 0) ...[
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppColors.warning.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: AppColors.warning.withValues(alpha: 0.5)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.local_fire_department_rounded,
                          color: AppColors.warning, size: 14),
                      const SizedBox(width: 4),
                      Text('${svc.streakDays}d',
                          style: const TextStyle(
                              color: AppColors.warning,
                              fontWeight: FontWeight.w800,
                              fontSize: 12)),
                    ],
                  ),
                )
              ]
            ],
          ),
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: LinearProgressIndicator(
              value: pct,
              minHeight: 8,
              backgroundColor: AppColors.surfaceHigh,
              valueColor: const AlwaysStoppedAnimation(AppColors.accent),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms);
  }
}

class _BadgeGrid extends StatelessWidget {
  final List badges;
  final bool earned;
  const _BadgeGrid({required this.badges, required this.earned});

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: badges.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 10,
        crossAxisSpacing: 10,
        childAspectRatio: 1.6,
      ),
      itemBuilder: (context, i) {
        final b = badges[i] as BadgeDef;
        return _BadgeTile(badge: b, earned: earned, index: i);
      },
    );
  }
}

class _BadgeTile extends StatelessWidget {
  final BadgeDef badge;
  final bool earned;
  final int index;
  const _BadgeTile(
      {required this.badge, required this.earned, required this.index});

  Color _rarityColor() {
    switch (badge.rarity) {
      case 'rare':
        return AppColors.accent;
      case 'epic':
        return const Color(0xFFB877FF);
      case 'legendary':
        return AppColors.warning;
      default:
        return AppColors.textSecondary;
    }
  }

  IconData _glyph() {
    switch (badge.icon) {
      case 'fire':
        return Icons.local_fire_department_rounded;
      case 'triangle':
        return Icons.change_history_rounded;
      case 'trophy':
        return Icons.emoji_events_rounded;
      case 'swords':
        return Icons.bolt_rounded;
      case 'doc_check':
        return Icons.task_alt_rounded;
      case 'radar':
        return Icons.radar_rounded;
      case 'crown':
        return Icons.workspace_premium_rounded;
      case 'checkmark':
        return Icons.check_circle_rounded;
      case 'compass':
        return Icons.explore_rounded;
      case 'book':
        return Icons.menu_book_rounded;
      case 'footsteps':
      default:
        return Icons.directions_walk_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _rarityColor();
    return GlassCard(
      padding: const EdgeInsets.all(12),
      borderColor: earned
          ? color.withValues(alpha: 0.45)
          : AppColors.border,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: earned ? 0.18 : 0.05),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                      color: color.withValues(alpha: earned ? 0.5 : 0.2)),
                ),
                child: Icon(_glyph(),
                    color: earned
                        ? color
                        : AppColors.textMuted.withValues(alpha: 0.7),
                    size: 18),
              ),
              const Spacer(),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(badge.rarity.toUpperCase(),
                    style: TextStyle(
                        color: color,
                        fontSize: 9,
                        letterSpacing: 1.0,
                        fontWeight: FontWeight.w800)),
              )
            ],
          ),
          const SizedBox(height: 8),
          Text(badge.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  color: earned ? AppColors.textPrimary : AppColors.textMuted,
                  fontWeight: FontWeight.w800,
                  fontSize: 12.5)),
          const SizedBox(height: 2),
          Expanded(
            child: Text(
              badge.description,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 10.5,
                  height: 1.35),
            ),
          ),
          Text('+${badge.xpReward} XP',
              style: TextStyle(
                  color: earned ? color : AppColors.textMuted,
                  fontSize: 10,
                  fontWeight: FontWeight.w800)),
        ],
      ),
    ).animate().fadeIn(duration: 240.ms, delay: (30 * index).ms);
  }
}
