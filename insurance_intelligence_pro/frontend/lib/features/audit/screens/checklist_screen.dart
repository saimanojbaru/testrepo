import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/hyperlinked_text.dart';
import '../../gamification/services/xp_service.dart';
import '../state/checklist_state.dart';

/// Detail screen for one audit template (e.g., "Loss & LAE Reserves").
/// Items are grouped under tabs for each phase; tapping the checkbox
/// completes the item and records an XP event.
class ChecklistScreen extends StatefulWidget {
  final ChecklistTemplate template;
  const ChecklistScreen({super.key, required this.template});

  @override
  State<ChecklistScreen> createState() => _ChecklistScreenState();
}

class _ChecklistScreenState extends State<ChecklistScreen>
    with TickerProviderStateMixin {
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    final phases = ChecklistState.instance.catalog.phases;
    _tabs = TabController(length: phases.length, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final phases = ChecklistState.instance.catalog.phases;
    final t = widget.template;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(t.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 16)),
        bottom: TabBar(
          controller: _tabs,
          isScrollable: true,
          indicatorColor: AppColors.accent,
          indicatorWeight: 2.5,
          labelColor: AppColors.accent,
          unselectedLabelColor: AppColors.textMuted,
          labelStyle: const TextStyle(
              fontWeight: FontWeight.w700, fontSize: 12, letterSpacing: 0.4),
          tabs: phases.map((p) => Tab(text: p.label)).toList(),
        ),
      ),
      body: AnimatedBuilder(
        animation: ChecklistState.instance,
        builder: (context, _) {
          final stats = ChecklistState.instance.stats(t);
          return Column(
            children: [
              _HeaderCard(template: t, stats: stats),
              Expanded(
                child: TabBarView(
                  controller: _tabs,
                  children: phases
                      .map((p) => _PhaseList(template: t, phase: p))
                      .toList(),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _HeaderCard extends StatelessWidget {
  final ChecklistTemplate template;
  final TemplateStats stats;
  const _HeaderCard({required this.template, required this.stats});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: GlassCard(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF18284A), Color(0xFF0B1426)],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(template.subtitle,
                style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12.5,
                    height: 1.45)),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: LinearProgressIndicator(
                      value: stats.progress,
                      minHeight: 8,
                      backgroundColor: AppColors.surfaceHigh,
                      valueColor:
                          const AlwaysStoppedAnimation(AppColors.accent),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text('${stats.completed}/${stats.total}',
                    style: const TextStyle(
                        color: AppColors.accent,
                        fontWeight: FontWeight.w800,
                        fontSize: 13)),
                if (stats.flagged > 0) ...[
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.negative.withValues(alpha: 0.16),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(
                          color:
                              AppColors.negative.withValues(alpha: 0.5)),
                    ),
                    child: Text('${stats.flagged} risks',
                        style: const TextStyle(
                            color: AppColors.negative,
                            fontWeight: FontWeight.w800,
                            fontSize: 10,
                            letterSpacing: 0.8)),
                  ),
                ]
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _PhaseList extends StatelessWidget {
  final ChecklistTemplate template;
  final PhaseDef phase;
  const _PhaseList({required this.template, required this.phase});

  @override
  Widget build(BuildContext context) {
    final items = template.itemsForPhase(phase.key);
    if (items.isEmpty) {
      return Center(
        child: Text('No items in ${phase.label}.',
            style: const TextStyle(color: AppColors.textMuted)),
      );
    }
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
      itemCount: items.length,
      itemBuilder: (context, i) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: _ItemRow(template: template, item: items[i], index: i),
        );
      },
    );
  }
}

class _ItemRow extends StatelessWidget {
  final ChecklistTemplate template;
  final ChecklistItem item;
  final int index;
  const _ItemRow(
      {required this.template, required this.item, required this.index});

  @override
  Widget build(BuildContext context) {
    final state = ChecklistState.instance.itemState(item.id);
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              GestureDetector(
                onTap: () async {
                  final newVal = !state.completed;
                  await ChecklistState.instance
                      .setCompleted(item.id, newVal);
                  if (newVal) {
                    final newly = await XpService.instance.recordEvent(
                        'checklist_item_completed',
                        xpDelta: 10);
                    // Phase-completed bonus
                    final stats = ChecklistState.instance.stats(template);
                    final phaseItems = template
                        .itemsForPhase(item.phase)
                        .map((i) => ChecklistState.instance.itemState(i.id));
                    if (phaseItems.every((s) => s.completed)) {
                      await XpService.instance
                          .recordEvent('phase_completed', xpDelta: 50);
                    }
                    if (newly.isNotEmpty && context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        backgroundColor: AppColors.surfaceHigh,
                        content: Text(
                          '🏆 Badge unlocked: ${newly.first.name} (+${newly.first.xpReward} XP)',
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w700),
                        ),
                      ));
                    }
                    // Suppress unused-variable lint.
                    // ignore: unused_local_variable
                    final _ = stats;
                  }
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 22,
                  height: 22,
                  margin: const EdgeInsets.only(top: 1, right: 12),
                  decoration: BoxDecoration(
                    color: state.completed
                        ? AppColors.accent
                        : Colors.transparent,
                    border: Border.all(
                        color: state.completed
                            ? AppColors.accent
                            : AppColors.border),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: state.completed
                      ? const Icon(Icons.check,
                          color: Colors.white, size: 14)
                      : null,
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    HyperlinkedText(
                      item.task,
                      style: TextStyle(
                          color: state.completed
                              ? AppColors.textMuted
                              : AppColors.textPrimary,
                          decoration: state.completed
                              ? TextDecoration.lineThrough
                              : null,
                          decorationColor: AppColors.textMuted,
                          fontSize: 13,
                          height: 1.4,
                          fontWeight: FontWeight.w600),
                    ),
                    if (item.guidance.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      HyperlinkedText(
                        'Guidance: ${item.guidance}',
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontSize: 11,
                            fontWeight: FontWeight.w700),
                      ),
                    ]
                  ],
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints.tightFor(
                    width: 28, height: 28),
                onPressed: () => ChecklistState.instance
                    .setFlagged(item.id, !state.flagged),
                icon: Icon(
                  state.flagged
                      ? Icons.flag_rounded
                      : Icons.outlined_flag_rounded,
                  color: state.flagged
                      ? AppColors.negative
                      : AppColors.textMuted,
                  size: 16,
                ),
                tooltip: state.flagged ? 'Unflag' : 'Flag as risk',
              ),
            ],
          ),
          const SizedBox(height: 6),
          TextField(
            controller:
                TextEditingController(text: state.note),
            onSubmitted: (v) =>
                ChecklistState.instance.setNote(item.id, v),
            style: const TextStyle(
                color: AppColors.textPrimary, fontSize: 12),
            decoration: const InputDecoration(
              isDense: true,
              contentPadding:
                  EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              hintText: 'Add note (press enter to save)…',
              hintStyle:
                  TextStyle(color: AppColors.textMuted, fontSize: 11.5),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 220.ms, delay: (24 * index).ms);
  }
}
