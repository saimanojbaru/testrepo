import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../bridge/screens/gaap_sap_bridge_screen.dart';
import '../../reserve/screens/reserve_toolkit_screen.dart';
import '../state/checklist_state.dart';
import 'checklist_screen.dart';

/// Audit Tools index — lists every checklist template with progress.
class AuditToolsScreen extends StatelessWidget {
  const AuditToolsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Audit Tools',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
      ),
      body: AnimatedBuilder(
        animation: ChecklistState.instance,
        builder: (context, _) {
          final templates = ChecklistState.instance.catalog.templates;
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 6),
                child: Text(
                  'Phase-based audit checklists covering planning, risk '
                  'assessment, controls, substantive testing, and completion. '
                  'Mark items complete, flag risks, and capture notes — '
                  'progress is saved locally.',
                  style: TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 12.5,
                      height: 1.45),
                ),
              ),
              const SizedBox(height: 12),
              // Quick-access toolkit row
              Row(
                children: [
                  Expanded(
                    child: _ToolkitButton(
                      icon: Icons.compare_arrows_rounded,
                      label: 'GAAP ↔ SAP Bridge',
                      onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                              builder: (_) =>
                                  const GaapSapBridgeScreen())),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _ToolkitButton(
                      icon: Icons.change_history_rounded,
                      label: 'Reserve Toolkit',
                      onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                              builder: (_) =>
                                  const ReserveToolkitScreen())),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              ...templates.map((t) {
                final stats = ChecklistState.instance.stats(t);
                final i = templates.indexOf(t);
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _TemplateTile(
                      template: t, stats: stats, index: i),
                );
              }),
            ],
          );
        },
      ),
    );
  }
}

class _ToolkitButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _ToolkitButton(
      {required this.icon, required this.label, required this.onTap});
  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(14),
            boxShadow: AppColors.glow(AppColors.accent, radius: 12),
          ),
          child: Row(
            children: [
              Icon(icon, color: Colors.white, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Text(label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 12.5,
                        letterSpacing: 0.3)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TemplateTile extends StatelessWidget {
  final ChecklistTemplate template;
  final TemplateStats stats;
  final int index;
  const _TemplateTile(
      {required this.template,
      required this.stats,
      required this.index});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ChecklistScreen(template: template))),
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: const Icon(Icons.fact_check_outlined,
                    color: Colors.white, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(template.name,
                        style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.w800,
                            fontSize: 14)),
                    const SizedBox(height: 2),
                    Text(template.subtitle,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontSize: 12,
                            height: 1.35)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: stats.progress,
              minHeight: 6,
              backgroundColor: AppColors.surfaceHigh,
              valueColor:
                  const AlwaysStoppedAnimation(AppColors.accent),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Text('${stats.completed}/${stats.total} items',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11)),
              const SizedBox(width: 8),
              if (stats.flagged > 0)
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.negative.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(
                        color:
                            AppColors.negative.withValues(alpha: 0.5)),
                  ),
                  child: Text('${stats.flagged} risks',
                      style: const TextStyle(
                          color: AppColors.negative,
                          fontWeight: FontWeight.w800,
                          fontSize: 10)),
                ),
              const Spacer(),
              const Text('Open',
                  style: TextStyle(
                      color: AppColors.accent,
                      fontSize: 11,
                      fontWeight: FontWeight.w700)),
              const SizedBox(width: 4),
              const Icon(Icons.arrow_forward_ios_rounded,
                  color: AppColors.accent, size: 11),
            ],
          ),
          if (template.tags.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: template.tags
                  .map((t) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 7, vertical: 3),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceHigh,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: AppColors.border),
                        ),
                        child: Text(t,
                            style: const TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 10,
                                fontWeight: FontWeight.w600)),
                      ))
                  .toList(),
            ),
          ],
        ],
      ),
    ).animate().fadeIn(duration: 260.ms, delay: (30 * index).ms);
  }
}
