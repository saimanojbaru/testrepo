import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../data/local_data.dart';
import '../models/product.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import 'product_detail_screen.dart';

/// Product Library index — top-down by category (Life, Annuity,
/// Institutional, P&C, Health), with a global search.
class ProductsIndexScreen extends StatefulWidget {
  const ProductsIndexScreen({super.key});

  @override
  State<ProductsIndexScreen> createState() => _ProductsIndexScreenState();
}

class _ProductsIndexScreenState extends State<ProductsIndexScreen> {
  final TextEditingController _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final lib = LocalData.instance.productLibrary;
    final q = _query.toLowerCase().trim();
    final filtered = q.isEmpty
        ? lib.products
        : lib.products.where((p) => p.searchCorpus.contains(q)).toList();

    // Group by category preserving the requested order.
    final grouped = <String, List<InsuranceProduct>>{};
    for (final c in lib.categories) {
      grouped[c.key] = filtered.where((p) => p.category == c.key).toList();
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Product Library',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Text(
              '${lib.products.length} US insurance products across '
              '${lib.categories.length} categories — every entry classified '
              'as Insurance Contract or Investment-Type Contract with explicit '
              'guidance mapping.',
              style: const TextStyle(
                  color: AppColors.textMuted, fontSize: 12.5, height: 1.4),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _search,
            onChanged: (v) => setState(() => _query = v),
            decoration: InputDecoration(
              hintText: 'Search products, guidance, KPIs…',
              prefixIcon: const Icon(Icons.search_rounded,
                  color: AppColors.textMuted),
              suffixIcon: _query.isEmpty
                  ? null
                  : IconButton(
                      onPressed: () {
                        _search.clear();
                        setState(() => _query = '');
                      },
                      icon: const Icon(Icons.close_rounded,
                          color: AppColors.textMuted),
                    ),
            ),
          ),
          const SizedBox(height: 18),
          for (final c in lib.categories) ...[
            if ((grouped[c.key] ?? []).isNotEmpty) ...[
              _CategoryHeader(label: c.label, count: grouped[c.key]!.length),
              const SizedBox(height: 8),
              for (var i = 0; i < grouped[c.key]!.length; i++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _ProductTile(
                      product: grouped[c.key]![i], index: i),
                ),
              const SizedBox(height: 14),
            ],
          ],
          if (filtered.isEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Text('No products match "$_query"',
                    style:
                        const TextStyle(color: AppColors.textMuted)),
              ),
            ),
        ],
      ),
    );
  }
}

class _CategoryHeader extends StatelessWidget {
  final String label;
  final int count;
  const _CategoryHeader({required this.label, required this.count});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 4,
          height: 18,
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            label.toUpperCase(),
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 13,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4),
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppColors.border),
          ),
          child: Text('$count',
              style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700)),
        ),
      ],
    );
  }
}

class _ProductTile extends StatelessWidget {
  final InsuranceProduct product;
  final int index;
  const _ProductTile({required this.product, required this.index});

  Color get _classColor =>
      product.isInsuranceContract ? AppColors.accent : AppColors.warning;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ProductDetailScreen(product: product))),
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(product.name,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 14.5,
                        height: 1.25)),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: _classColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                      color: _classColor.withValues(alpha: 0.5)),
                ),
                child: Text(
                  product.isInsuranceContract
                      ? 'INSURANCE'
                      : 'INVESTMENT',
                  style: TextStyle(
                      color: _classColor,
                      fontSize: 9.5,
                      letterSpacing: 1.2,
                      fontWeight: FontWeight.w800),
                ),
              ),
            ],
          ),
          if (product.riskType.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text('Risk · ${product.riskType}',
                style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 11,
                    letterSpacing: 0.6,
                    fontWeight: FontWeight.w700)),
          ],
          const SizedBox(height: 6),
          Text(product.briefIntro,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 12.5,
                  height: 1.45)),
          const SizedBox(height: 10),
          Row(
            children: [
              Text(
                '${product.guidance.length} guidance refs · ${product.kpis.length} KPIs',
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
    ).animate().fadeIn(duration: 280.ms, delay: (30 * index).ms);
  }
}
