import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/insight.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

/// Drill-down screen for a single industry update / news headline.
///
/// Surfaces source attribution prominently in the AppBar (top-right),
/// renders the full summary, impact rationale, and a tappable
/// "Open primary source" CTA.
class NewsDetailScreen extends StatelessWidget {
  final NewsItem item;
  final List<NewsItem> related;
  const NewsDetailScreen({
    super.key,
    required this.item,
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

  Future<void> _open() async {
    if (item.url.isEmpty) return;
    final uri = Uri.tryParse(item.url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final color = _impactColor();
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text(
          'Industry Update',
          style: TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w700,
              fontSize: 16),
        ),
        actions: [
          // Source name in the top-right per request
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(
                children: [
                  const Icon(Icons.public,
                      color: AppColors.accent, size: 12),
                  const SizedBox(width: 6),
                  Text(item.source.toUpperCase(),
                      style: const TextStyle(
                          color: AppColors.accent,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.2,
                          fontSize: 10.5)),
                ],
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          GlassCard(
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
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                            color: color.withValues(alpha: 0.5)),
                      ),
                      child: Text('${item.impact.toUpperCase()} IMPACT',
                          style: TextStyle(
                              color: color,
                              fontWeight: FontWeight.w800,
                              fontSize: 10,
                              letterSpacing: 1.4)),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceHigh,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: AppColors.border),
                      ),
                      child: Text(item.category.toUpperCase(),
                          style: const TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: 10,
                              letterSpacing: 1.2,
                              fontWeight: FontWeight.w700)),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(item.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 22,
                        height: 1.25)),
                const SizedBox(height: 12),
                if ((item.summary ?? '').isNotEmpty)
                  Text(item.summary!,
                      style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 13.5,
                          height: 1.55)),
                const SizedBox(height: 14),
                Row(
                  children: [
                    const Icon(Icons.calendar_today_outlined,
                        color: AppColors.textMuted, size: 12),
                    const SizedBox(width: 6),
                    Text(Formatters.date(item.published),
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 11.5)),
                    const Spacer(),
                    Text(item.source,
                        style: const TextStyle(
                            color: AppColors.textMuted,
                            fontSize: 11.5,
                            fontWeight: FontWeight.w600)),
                  ],
                ),
              ],
            ),
          ).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05),
          const SizedBox(height: 18),
          if (item.url.isNotEmpty)
            GlassCard(
              onTap: _open,
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      gradient: AppColors.primaryGradient,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.open_in_new_rounded,
                        color: Colors.white, size: 18),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('OPEN PRIMARY SOURCE',
                            style: TextStyle(
                                color: AppColors.accent,
                                fontSize: 10,
                                letterSpacing: 1.4,
                                fontWeight: FontWeight.w800)),
                        const SizedBox(height: 4),
                        Text(item.source,
                            style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontWeight: FontWeight.w700,
                                fontSize: 14)),
                        const SizedBox(height: 2),
                        Text(item.url,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 11.5)),
                      ],
                    ),
                  ),
                  const Icon(Icons.arrow_forward_ios_rounded,
                      color: AppColors.textMuted, size: 14),
                ],
              ),
            ),
          const SizedBox(height: 18),
          const SectionHeader(title: 'Why this matters'),
          GlassCard(
            child: Text(
              _whyThisMatters(item),
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.55),
            ),
          ),
          if (related.isNotEmpty) ...[
            const SizedBox(height: 18),
            const SectionHeader(title: 'Related updates'),
            for (var i = 0; i < related.length; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _RelatedTile(item: related[i]),
              ),
          ],
        ],
      ),
    );
  }

  String _whyThisMatters(NewsItem item) {
    switch (item.category) {
      case 'Regulation':
        return 'Regulatory updates set the floor on capital, reserving, and disclosure. Watch for downstream RBC, statutory-reserve, or GAAP-disclosure implications. NAIC/SEC actions tend to flow through to all US-domiciled and US-listed insurers within 6–18 months.';
      case 'Catastrophe':
        return 'Catastrophe events reset reinsurance pricing and capital deployment for the next renewal cycle. Watch for adjustment of cat-bond spreads, retrocession capacity, and primary-insurer attachment points.';
      case 'Reinsurance':
        return 'Reinsurance pricing and capacity are leading indicators for primary-insurer net retention and combined-ratio expectations. Hard markets compress primary margins; softening markets release capital back to cedents.';
      case 'Earnings':
        return 'Industry earnings updates calibrate analyst expectations on combined-ratio, investment yield, and reserve-development trends. Useful to stress-test single-name forecasts against the broader cycle.';
      case 'M&A':
        return 'Deal activity in insurance affects market structure, scale economies, and competitive intensity. Watch for VOBA / goodwill recognition under ASC 805 and statutory goodwill admission limits under SSAP 68.';
      case 'Climate':
        return 'Climate-driven losses and ESG disclosure requirements are reshaping the long-term liability profile of P&C books and the asset side of life portfolios.';
      case 'Cyber':
        return 'Cyber line is one of the fastest-growing P&C segments. Loss volatility, modeling immaturity, and systemic-risk concentration make it a key watch-item for both primary and reinsurance capital allocations.';
      case 'Health':
        return 'Health insurer profitability is bracketed by ACA medical-loss-ratio minimums, Medicare Advantage rate notices, and CMS Star Ratings. Each can move EPS by 5-15% in a single quarter.';
      default:
        return 'This update may move sector valuations, regulatory expectations, or competitive dynamics. Cross-reference with the company drill-downs to see which carriers are most exposed.';
    }
  }
}

class _RelatedTile extends StatelessWidget {
  final NewsItem item;
  const _RelatedTile({required this.item});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => NewsDetailScreen(item: item))),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(item.source.toUpperCase(),
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontSize: 9.5,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(width: 6),
                    Text(item.category,
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10)),
                  ],
                ),
                const SizedBox(height: 4),
                Text(item.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        height: 1.3)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          const Icon(Icons.arrow_forward_ios_rounded,
              color: AppColors.textMuted, size: 12),
        ],
      ),
    );
  }
}
