import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

class KnowledgeScreen extends StatefulWidget {
  const KnowledgeScreen({super.key});

  @override
  State<KnowledgeScreen> createState() => _KnowledgeScreenState();
}

class _KnowledgeScreenState extends State<KnowledgeScreen> {
  final TextEditingController _search = TextEditingController();
  Timer? _debounce;
  String _framework = 'All';
  String _query = '';

  @override
  void initState() {
    super.initState();
    _search.addListener(_onChanged);
  }

  @override
  void dispose() {
    _search.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 120), () {
      setState(() => _query = _search.text.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    final svc = AnalyticsService.instance;
    final frameworks = svc.knowledgeFrameworks();
    final List<KnowledgeArticle> articles = (_query.isEmpty
            ? svc.knowledge(framework: _framework)
            : svc.knowledgeSearch(_query))
        .map((m) =>
            KnowledgeArticle.fromJson(Map<String, dynamic>.from(m)))
        .toList();

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 120),
        children: [
          const Text(
            'Knowledge',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 26,
                letterSpacing: -0.4),
          ),
          const SizedBox(height: 6),
          const Text(
            'Insurance accounting, demystified. ASC 944, FAS 60/97/133, SAP.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 13),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _search,
            decoration: const InputDecoration(
              hintText: 'Search standards, FSLI, GAAP vs STAT…',
              prefixIcon:
                  Icon(Icons.search_rounded, color: AppColors.textMuted),
            ),
          ),
          const SizedBox(height: 14),
          SizedBox(
            height: 36,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                for (final f in frameworks)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: _FrameworkChip(
                      label: f,
                      selected: f == _framework,
                      onTap: () {
                        _search.clear();
                        setState(() {
                          _framework = f;
                          _query = '';
                        });
                      },
                    ),
                  )
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (articles.isEmpty)
            GlassCard(
              child: const Text('No articles match.',
                  style: TextStyle(color: AppColors.textMuted)),
            )
          else
            ...List.generate(
              articles.length,
              (i) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _ArticleCard(article: articles[i], index: i),
              ),
            ),
        ],
      ),
    );
  }
}

class _FrameworkChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _FrameworkChip(
      {required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            gradient: selected ? AppColors.primaryGradient : null,
            color: selected ? null : AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
                color: selected ? Colors.transparent : AppColors.border),
          ),
          child: Text(label,
              style: TextStyle(
                color: selected ? Colors.white : AppColors.textSecondary,
                fontWeight: FontWeight.w700,
                fontSize: 11.5,
                letterSpacing: 0.6,
              )),
        ),
      ),
    );
  }
}

class _ArticleCard extends StatelessWidget {
  final KnowledgeArticle article;
  final int index;
  const _ArticleCard({required this.article, required this.index});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: () => _showDetail(context, article),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(article.framework,
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        letterSpacing: 1.2,
                        fontWeight: FontWeight.w700)),
              ),
              const SizedBox(width: 8),
              if (article.fsli != null && article.fsli!.isNotEmpty)
                Expanded(
                  child: Text(article.fsli!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 11)),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Text(article.title,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                  height: 1.3)),
          const SizedBox(height: 6),
          Text(article.summary,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 12.5,
                  height: 1.45)),
          if (article.tags.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: article.tags
                  .map((t) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceHigh,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: AppColors.border),
                        ),
                        child: Text(t,
                            style: const TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 10,
                                letterSpacing: 0.8,
                                fontWeight: FontWeight.w600)),
                      ))
                  .toList(),
            ),
          ],
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (50 * index).ms);
  }

  void _showDetail(BuildContext context, KnowledgeArticle a) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.background,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (ctx) {
        return DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.85,
          minChildSize: 0.4,
          maxChildSize: 0.95,
          builder: (_, controller) => SingleChildScrollView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceHigh,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    gradient: AppColors.primaryGradient,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(a.framework,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          letterSpacing: 1.2,
                          fontWeight: FontWeight.w700)),
                ),
                const SizedBox(height: 10),
                Text(a.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 22,
                        height: 1.2)),
                const SizedBox(height: 12),
                Text(a.summary,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 13.5,
                        height: 1.5)),
                const SizedBox(height: 18),
                if ((a.gaapView ?? '').isNotEmpty ||
                    (a.statView ?? '').isNotEmpty)
                  _GaapStatTable(
                      gaap: a.gaapView ?? '–',
                      stat: a.statView ?? '–'),
                const SizedBox(height: 18),
                _MarkdownBody(text: a.bodyMd),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _GaapStatTable extends StatelessWidget {
  final String gaap;
  final String stat;
  const _GaapStatTable({required this.gaap, required this.stat});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SectionHeader(
            title: 'GAAP vs Statutory',
            padding: EdgeInsets.zero,
          ),
          const SizedBox(height: 8),
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: _Pane(label: 'GAAP', body: gaap)),
                const VerticalDivider(
                    color: AppColors.divider, width: 16, thickness: 1),
                Expanded(child: _Pane(label: 'STAT (SAP)', body: stat)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Pane extends StatelessWidget {
  final String label;
  final String body;
  const _Pane({required this.label, required this.body});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
                color: AppColors.accent,
                fontSize: 11,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4)),
        const SizedBox(height: 6),
        Text(body,
            style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 12.5,
                height: 1.5)),
      ],
    );
  }
}

class _MarkdownBody extends StatelessWidget {
  final String text;
  const _MarkdownBody({required this.text});

  @override
  Widget build(BuildContext context) {
    final lines = text.split('\n');
    final widgets = <Widget>[];
    for (final raw in lines) {
      final l = raw.trimRight();
      if (l.isEmpty) {
        widgets.add(const SizedBox(height: 8));
        continue;
      }
      if (l.startsWith('## ')) {
        widgets.add(Padding(
          padding: const EdgeInsets.only(top: 12, bottom: 6),
          child: Text(l.substring(3),
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w800,
                  fontSize: 16)),
        ));
      } else if (l.startsWith('- ')) {
        widgets.add(Padding(
          padding: const EdgeInsets.fromLTRB(8, 2, 0, 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 8, right: 8),
                child: SizedBox(
                    width: 4,
                    height: 4,
                    child: DecoratedBox(
                        decoration: BoxDecoration(
                            color: AppColors.accent,
                            shape: BoxShape.circle))),
              ),
              Expanded(
                child: Text(l.substring(2).replaceAll('**', ''),
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.5)),
              ),
            ],
          ),
        ));
      } else if (l.startsWith('```')) {
        continue;
      } else {
        widgets.add(Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Text(l.replaceAll('**', '').replaceAll('`', ''),
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.55)),
        ));
      }
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: widgets,
    );
  }
}
