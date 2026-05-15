import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import 'knowledge_detail_screen.dart';

class KnowledgeScreen extends StatefulWidget {
  const KnowledgeScreen({super.key});

  @override
  State<KnowledgeScreen> createState() => _KnowledgeScreenState();
}

class _KnowledgeScreenState extends State<KnowledgeScreen> {
  final TextEditingController _search = TextEditingController();
  Timer? _debounce;
  String _framework = 'All';
  String _depth = 'All';
  String _query = '';

  static const _depths = ['All', 'deep', 'overview'];

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

  List<Map<String, dynamic>> _resultsRaw() {
    final svc = AnalyticsService.instance;
    if (_query.isNotEmpty) return svc.knowledgeSearch(_query);
    return svc.knowledge(framework: _framework);
  }

  List<KnowledgeArticle> _results() {
    var list = _resultsRaw()
        .map((m) => KnowledgeArticle.fromJson(Map<String, dynamic>.from(m)))
        .toList();
    if (_depth != 'All') {
      list = list.where((a) => a.depth == _depth).toList();
    }
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final svc = AnalyticsService.instance;
    final frameworks = svc.knowledgeFrameworks();
    final articles = _results();
    final hasQuery = _query.isNotEmpty;

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
            '29 articles. ASC 944, FAS 60/97/133, ASC 320/321/326/805/740, '
            'SAP, RBC, plus FSLI-level GAAP-vs-STAT comparisons.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 13, height: 1.4),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _search,
            decoration: InputDecoration(
              hintText: 'Search "cash", "reserves", "DAC", "MRB", "CECL"...',
              prefixIcon: const Icon(Icons.search_rounded,
                  color: AppColors.textMuted),
              suffixIcon: _search.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.close_rounded,
                          color: AppColors.textMuted),
                      onPressed: () => _search.clear(),
                    )
                  : null,
            ),
          ),
          const SizedBox(height: 12),
          // Depth filter
          if (!hasQuery) ...[
            SizedBox(
              height: 32,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  for (final d in _depths)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: _PillChip(
                        label: d == 'All'
                            ? 'All depth'
                            : (d == 'deep' ? 'Deep dive' : 'Overview'),
                        selected: d == _depth,
                        onTap: () => setState(() => _depth = d),
                      ),
                    )
                ],
              ),
            ),
            const SizedBox(height: 8),
          ],
          // Framework filter
          if (!hasQuery)
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
                        onTap: () => setState(() => _framework = f),
                      ),
                    )
                ],
              ),
            ),
          const SizedBox(height: 16),
          // Result count or query banner
          if (hasQuery)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Text(
                articles.isEmpty
                    ? 'No matches for "$_query"'
                    : '${articles.length} match${articles.length == 1 ? "" : "es"} for "$_query"',
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 12),
              ),
            ),
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
                child: _ArticleCard(
                  article: articles[i],
                  index: i,
                  query: _query,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _PillChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _PillChip(
      {required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.accent.withValues(alpha: 0.18)
                : AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
                color: selected
                    ? AppColors.accent.withValues(alpha: 0.7)
                    : AppColors.border),
          ),
          child: Text(label,
              style: TextStyle(
                color: selected ? AppColors.accent : AppColors.textMuted,
                fontWeight: FontWeight.w700,
                fontSize: 11,
                letterSpacing: 0.6,
              )),
        ),
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
  final String query;
  const _ArticleCard(
      {required this.article, required this.index, required this.query});

  @override
  Widget build(BuildContext context) {
    final isDeep = article.depth == 'deep';
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => KnowledgeDetailScreen(article: article))),
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
              if (isDeep)
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 7, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.positive.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(
                        color: AppColors.positive.withValues(alpha: 0.45)),
                  ),
                  child: const Text('DEEP',
                      style: TextStyle(
                          color: AppColors.positive,
                          fontSize: 9.5,
                          letterSpacing: 1.2,
                          fontWeight: FontWeight.w700)),
                ),
              const Spacer(),
              if (article.fsliTable.isNotEmpty)
                Text(
                  '${article.fsliTable.length} FSLI ${article.fsliTable.length == 1 ? "row" : "rows"}',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 10.5),
                ),
            ],
          ),
          const SizedBox(height: 10),
          if ((article.fsli ?? '').isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                'FSLI · ${article.fsli}',
                style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 11,
                    letterSpacing: 0.6,
                    fontWeight: FontWeight.w700),
              ),
            ),
          Text(article.title,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w700,
                  fontSize: 15.5,
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
                  .take(6)
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
                                letterSpacing: 0.4,
                                fontWeight: FontWeight.w600)),
                      ))
                  .toList(),
            ),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              if (article.fsliTable.isNotEmpty)
                Text(
                  '${article.fsliTable.length} FSLI rows · ${article.references.length} sources',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 10.5),
                )
              else if (article.references.isNotEmpty)
                Text(
                  '${article.references.length} sources',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 10.5),
                ),
              const Spacer(),
              const Text('Open',
                  style: TextStyle(
                      color: AppColors.accent,
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700)),
              const SizedBox(width: 4),
              const Icon(Icons.arrow_forward_ios_rounded,
                  color: AppColors.accent, size: 11),
            ],
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (40 * index).ms);
  }
}
