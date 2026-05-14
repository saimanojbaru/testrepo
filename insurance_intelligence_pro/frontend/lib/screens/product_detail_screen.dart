import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/local_data.dart';
import '../models/product.dart';
import '../models/standard.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import '../widgets/hyperlinked_text.dart';
import '../widgets/section_header.dart';
import 'standard_detail_screen.dart';

/// Deep-dive screen for a single insurance product.
///
/// Renders: classification badge, brief intro, detailed description,
/// key features, **internal-linked guidance** (every SSAP / ASC / FAS
/// reference is tappable), KPIs, market context, and an authoritative
/// external source link at the bottom.
class ProductDetailScreen extends StatelessWidget {
  final InsuranceProduct product;
  const ProductDetailScreen({super.key, required this.product});

  Future<void> _openSource() async {
    final uri = Uri.tryParse(product.sourceLink);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  Color get _classColor =>
      product.isInsuranceContract ? AppColors.accent : AppColors.warning;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(
          product.name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w800,
              fontSize: 16),
        ),
        actions: [
          IconButton(
            tooltip: 'Open authoritative source',
            icon: const Icon(Icons.open_in_new_rounded,
                color: AppColors.accent),
            onPressed: _openSource,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          _Hero(product: product, classColor: _classColor),
          const SizedBox(height: 18),

          // 2-3 sentence intro
          const SectionHeader(
            title: 'Brief intro',
            subtitle: '2-3 sentences on the product\'s function',
          ),
          GlassCard(
            child: HyperlinkedText(
              product.briefIntro,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  height: 1.55),
            ),
          ).animate().fadeIn(duration: 350.ms),
          const SizedBox(height: 18),

          // Detailed description (with auto-linked SSAP / ASC / FAS refs)
          const SectionHeader(
            title: 'How it works',
            subtitle: 'Detailed accounting + risk profile',
          ),
          GlassCard(
            child: HyperlinkedText(
              product.description,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.6),
            ),
          ),
          const SizedBox(height: 18),

          if (product.keyFeatures.isNotEmpty) ...[
            const SectionHeader(title: 'Key features'),
            GlassCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: product.keyFeatures
                    .map((f) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Padding(
                                padding: EdgeInsets.only(top: 7, right: 10),
                                child: SizedBox(
                                  width: 5,
                                  height: 5,
                                  child: DecoratedBox(
                                      decoration: BoxDecoration(
                                          color: AppColors.accent,
                                          shape: BoxShape.circle)),
                                ),
                              ),
                              Expanded(
                                child: HyperlinkedText(f,
                                    style: const TextStyle(
                                        color: AppColors.textSecondary,
                                        fontSize: 13,
                                        height: 1.5)),
                              ),
                            ],
                          ),
                        ))
                    .toList(),
              ),
            ),
            const SizedBox(height: 18),
          ],

          // Guidance mapping — internal links to SSAP / ASC / PCAOB
          const SectionHeader(
            title: 'Guidance mapping',
            subtitle: 'Tap any standard to open the deep dive',
          ),
          GlassCard(
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                for (var i = 0; i < product.guidance.length; i++)
                  _GuidanceTile(
                    ref: product.guidance[i],
                    isLast: i == product.guidance.length - 1,
                  ),
              ],
            ),
          ),
          const SizedBox(height: 18),

          if (product.kpis.isNotEmpty) ...[
            const SectionHeader(title: 'Operating KPIs'),
            GlassCard(
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: product.kpis
                    .map((k) => Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(20),
                            border:
                                Border.all(color: AppColors.border),
                          ),
                          child: Text(k,
                              style: const TextStyle(
                                  color: AppColors.textPrimary,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600)),
                        ))
                    .toList(),
              ),
            ),
            const SizedBox(height: 18),
          ],

          if (product.marketNote.isNotEmpty) ...[
            const SectionHeader(title: 'US market context'),
            GlassCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (product.marketSizeUsd != null) ...[
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Text(
                          _formatMarketSize(product.marketSizeUsd!),
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w800,
                              fontSize: 24,
                              letterSpacing: -0.4),
                        ),
                        const SizedBox(width: 6),
                        const Text('US market',
                            style: TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 12,
                                fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 8),
                  ],
                  HyperlinkedText(
                    product.marketNote,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.55),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
          ],

          if (product.tags.isNotEmpty) ...[
            const SectionHeader(title: 'Tags'),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: product.tags
                  .map((t) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceElevated,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: AppColors.border),
                        ),
                        child: Text(t,
                            style: const TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 11,
                                fontWeight: FontWeight.w600)),
                      ))
                  .toList(),
            ),
            const SizedBox(height: 18),
          ],

          // Authoritative external source — bottom of every product page
          _SourceFooter(url: product.sourceLink, onTap: _openSource),
        ],
      ),
    );
  }

  String _formatMarketSize(double n) {
    if (n >= 1e12) return '\$${(n / 1e12).toStringAsFixed(1)}T';
    if (n >= 1e9) return '\$${(n / 1e9).toStringAsFixed(0)}B';
    if (n >= 1e6) return '\$${(n / 1e6).toStringAsFixed(0)}M';
    return '\$${n.toStringAsFixed(0)}';
  }
}

class _Hero extends StatelessWidget {
  final InsuranceProduct product;
  final Color classColor;
  const _Hero({required this.product, required this.classColor});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: classColor.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                      color: classColor.withValues(alpha: 0.5)),
                ),
                child: Text(product.classification.toUpperCase(),
                    style: TextStyle(
                        color: classColor,
                        fontSize: 10.5,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w800)),
              ),
              if (product.riskType.isNotEmpty)
                _Chip(label: 'Risk: ${product.riskType}'),
              if (product.duration.isNotEmpty)
                _Chip(label: product.duration),
            ],
          ),
          const SizedBox(height: 12),
          Text(product.name,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w800,
                  fontSize: 22,
                  height: 1.25)),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms).slideY(begin: 0.05);
  }
}

class _Chip extends StatelessWidget {
  final String label;
  const _Chip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.surfaceHigh,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.border),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: AppColors.textSecondary,
            fontSize: 10.5,
            letterSpacing: 0.6,
            fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _GuidanceTile extends StatelessWidget {
  final GuidanceRef ref;
  final bool isLast;
  const _GuidanceTile({required this.ref, required this.isLast});

  Standard? _resolve() {
    try {
      final cat = LocalData.instance.catalogFor(ref.framework);
      for (final s in cat.standards) {
        if (s.id == ref.id) return s;
      }
    } catch (_) {}
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final target = _resolve();
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: target == null
            ? null
            : () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => StandardDetailScreen(standard: target))),
        child: Container(
          padding: const EdgeInsets.symmetric(
              horizontal: 14, vertical: 14),
          decoration: BoxDecoration(
            border: !isLast
                ? const Border(bottom: BorderSide(color: AppColors.divider))
                : null,
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(ref.label,
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 11,
                        letterSpacing: 0.6)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(ref.note,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12.5,
                        height: 1.4)),
              ),
              const SizedBox(width: 8),
              const Icon(Icons.arrow_forward_ios_rounded,
                  color: AppColors.accent, size: 12),
            ],
          ),
        ),
      ),
    );
  }
}

class _SourceFooter extends StatelessWidget {
  final String url;
  final VoidCallback onTap;
  const _SourceFooter({required this.url, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: onTap,
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: AppColors.accent.withValues(alpha: 0.4)),
            ),
            child: const Icon(Icons.public,
                color: AppColors.accent, size: 18),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('SOURCE & AUTHORITY',
                    style: TextStyle(
                        color: AppColors.accent,
                        fontSize: 10,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w800)),
                const SizedBox(height: 4),
                Text(url,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12)),
              ],
            ),
          ),
          const Icon(Icons.open_in_new_rounded,
              color: AppColors.accent, size: 16),
        ],
      ),
    );
  }
}
