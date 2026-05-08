import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/analytics_service.dart';
import '../services/api_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import 'news_detail_screen.dart';

class UpdatesScreen extends StatefulWidget {
  const UpdatesScreen({super.key});

  @override
  State<UpdatesScreen> createState() => _UpdatesScreenState();
}

class _UpdatesScreenState extends State<UpdatesScreen> {
  String _selected = 'All';
  List<NewsItem> _live = const [];
  bool _liveLoading = false;

  @override
  void initState() {
    super.initState();
    _refreshLive();
  }

  /// Fire-and-forget: try the configured backend for fresh news.
  /// Curated headlines stay visible while we wait; if the request
  /// succeeds, live items are merged on top.
  Future<void> _refreshLive() async {
    if (!ApiService.instance.isConfigured) {
      if (mounted) setState(() => _live = const []);
      return;
    }
    setState(() => _liveLoading = true);
    final raw = await ApiService.instance.liveNews(
        category: _selected == 'All' ? null : _selected, limit: 30);
    if (!mounted) return;
    setState(() {
      _live = raw.map((m) => NewsItem.fromJson(Map<String, dynamic>.from(m))).toList();
      _liveLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final result = AnalyticsService.instance.news(category: _selected);
    final curated = ((result['items'] as List?) ?? [])
        .map((e) =>
            NewsItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    // Merge live first, then curated, dedupe by title.
    final seen = <String>{};
    final merged = <NewsItem>[];
    for (final n in [..._live, ...curated]) {
      final key = n.title.trim().toLowerCase();
      if (seen.contains(key)) continue;
      seen.add(key);
      merged.add(n);
    }
    final items = merged;
    final categories = ((result['categories'] as List?) ?? ['All']).cast<String>();
    final isLive = _live.isNotEmpty;

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 120),
        children: [
          const Text(
            'Industry Updates',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 26,
                letterSpacing: -0.4),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(
                child: Text(
                  isLive
                      ? 'Live: ${_live.length} fresh + curated baseline'
                      : 'Curated regulatory, market, and rating-agency signals.',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 13),
                ),
              ),
              IconButton(
                onPressed: _liveLoading ? null : _refreshLive,
                icon: _liveLoading
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: AppColors.accent))
                    : const Icon(Icons.refresh_rounded,
                        color: AppColors.accent, size: 18),
                tooltip: 'Refresh live feed',
              )
            ],
          ),
          const SizedBox(height: 4),
          SizedBox(
            height: 36,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                for (final c in categories)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: _CategoryChip(
                      label: c,
                      selected: c == _selected,
                      onTap: () {
                        setState(() => _selected = c);
                        _refreshLive();
                      },
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          if (items.isEmpty)
            GlassCard(
              child: const Text('No updates in this category.',
                  style: TextStyle(color: AppColors.textMuted)),
            )
          else
            ...List.generate(
              items.length,
              (i) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _UpdateCard(
                  item: items[i],
                  index: i,
                  related: items
                      .where((n) => n.title != items[i].title)
                      .take(4)
                      .toList(),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _CategoryChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _CategoryChip(
      {required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 220),
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

class _UpdateCard extends StatelessWidget {
  final NewsItem item;
  final int index;
  final List<NewsItem> related;
  const _UpdateCard({
    required this.item,
    required this.index,
    this.related = const [],
  });

  Color _impactColor() {
    switch (item.impact) {
      case 'High':
        return AppColors.negative;
      case 'Low':
        return AppColors.textMuted;
      default:
        return AppColors.warning;
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _impactColor();
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) =>
              NewsDetailScreen(item: item, related: related))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: color.withValues(alpha: 0.45)),
                ),
                child: Text('${item.impact} impact',
                    style: TextStyle(
                        color: color,
                        fontSize: 10,
                        letterSpacing: 1.3,
                        fontWeight: FontWeight.w700)),
              ),
              const SizedBox(width: 8),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppColors.border),
                ),
                child: Text(item.category,
                    style: const TextStyle(
                        color: AppColors.accent,
                        fontSize: 10,
                        letterSpacing: 1.2,
                        fontWeight: FontWeight.w700)),
              ),
              const Spacer(),
              Text(item.source,
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontWeight: FontWeight.w600,
                      fontSize: 11)),
            ],
          ),
          const SizedBox(height: 10),
          Text(item.title,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w700,
                  fontSize: 14.5,
                  height: 1.3)),
          if ((item.summary ?? '').isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(item.summary!,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12.5,
                    height: 1.45)),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              Text(Formatters.date(item.published),
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11)),
            ],
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (40 * index).ms);
  }
}
