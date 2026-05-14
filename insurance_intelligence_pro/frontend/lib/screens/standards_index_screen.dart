import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../data/local_data.dart';
import '../models/standard.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import 'standard_detail_screen.dart';

/// Level 1 — Searchable index of every standard for a given framework
/// (SSAP, ASC 944, or PCAOB). Tapping a row drills down to the Level 2
/// four-part detail view.
class StandardsIndexScreen extends StatefulWidget {
  final String framework; // SSAP / ASC944 / PCAOB
  const StandardsIndexScreen({super.key, required this.framework});

  @override
  State<StandardsIndexScreen> createState() => _StandardsIndexScreenState();
}

class _StandardsIndexScreenState extends State<StandardsIndexScreen> {
  final TextEditingController _search = TextEditingController();
  String _query = '';

  String get _frameworkLabel {
    switch (widget.framework) {
      case 'SSAP':
        return 'SSAP';
      case 'ASC944':
        return 'ASC 944';
      case 'PCAOB':
        return 'PCAOB';
      default:
        return widget.framework;
    }
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final catalog = LocalData.instance.catalogFor(widget.framework);
    final all = catalog.standards;
    final q = _query.toLowerCase().trim();
    final filtered = q.isEmpty
        ? all
        : all.where((s) => s.searchCorpus.contains(q)).toList();

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(
          _frameworkLabel,
          style: const TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w800,
              fontSize: 18),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(36),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
            child: Text(
              catalog.manual,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: AppColors.textMuted, fontSize: 11.5, height: 1.35),
            ),
          ),
        ),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 6, 20, 12),
            child: TextField(
              controller: _search,
              onChanged: (v) => setState(() => _query = v),
              decoration: InputDecoration(
                hintText: 'Search ${all.length} standards…',
                prefixIcon: const Icon(Icons.search_rounded,
                    color: AppColors.textMuted),
                suffixIcon: _query.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded,
                            color: AppColors.textMuted),
                        onPressed: () {
                          _search.clear();
                          setState(() => _query = '');
                        },
                      ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: const BoxDecoration(
                      color: AppColors.positive, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(
                  'Source · ${catalog.publisher}',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11.5),
                ),
                const Spacer(),
                Text(
                  q.isEmpty
                      ? '${all.length} standards'
                      : '${filtered.length} of ${all.length}',
                  style: const TextStyle(
                      color: AppColors.textMuted, fontSize: 11.5),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: filtered.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Text(
                        'No matches for "$_query"',
                        style:
                            const TextStyle(color: AppColors.textMuted),
                      ),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                    itemCount: filtered.length,
                    itemBuilder: (context, i) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _StandardTile(
                          standard: filtered[i],
                          framework: widget.framework,
                          index: i),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

class _StandardTile extends StatelessWidget {
  final Standard standard;
  final String framework;
  final int index;
  const _StandardTile({
    required this.standard,
    required this.framework,
    required this.index,
  });

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => StandardDetailScreen(standard: standard))),
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 56,
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(8),
            ),
            alignment: Alignment.center,
            child: Text(
              // PCAOB tiles show the 4-digit number only ("2201", "2301")
              // — no "AS" prefix. SSAP / ASC tiles keep their identifier.
              standard.badgeNumber,
              maxLines: 1,
              textAlign: TextAlign.center,
              style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                  fontSize: standard.framework == 'PCAOB' ? 14 : 12,
                  letterSpacing: 0.4),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(standard.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        height: 1.3)),
                if ((standard.fsli ?? '').isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text('FSLI · ${standard.fsli}',
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontSize: 10.5,
                          letterSpacing: 0.4,
                          fontWeight: FontWeight.w700)),
                ],
                const SizedBox(height: 4),
                Text(standard.summary,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                        height: 1.4)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(
                      '${standard.evolution.length} updates · ${standard.gaapComparison.length} comparisons',
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 10.5),
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
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 280.ms, delay: (30 * index).ms);
  }
}
