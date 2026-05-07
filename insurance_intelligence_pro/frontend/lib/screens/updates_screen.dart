import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/api_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/animated_loader.dart';
import '../widgets/error_card.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

class UpdatesScreen extends StatefulWidget {
  const UpdatesScreen({super.key});

  @override
  State<UpdatesScreen> createState() => _UpdatesScreenState();
}

class _UpdatesScreenState extends State<UpdatesScreen> {
  String _selectedCategory = 'All';
  Map<String, dynamic>? _result;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({String? category}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final r = await ApiService.instance.updates(category: category);
      if (!mounted) return;
      setState(() => _result = r);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final items = ((_result?['items'] as List?) ?? [])
        .map((e) => NewsItem.fromJson(e as Map<String, dynamic>))
        .toList();
    final categories =
        ((_result?['categories'] as List?) ?? ['All']).cast<String>();

    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        color: AppColors.accent,
        backgroundColor: AppColors.surface,
        onRefresh: () => _load(category: _selectedCategory),
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
            Text('Curated from public RSS, NAIC, and SEC press releases.',
                style: TextStyle(color: AppColors.textMuted, fontSize: 13)),
            const SizedBox(height: 14),
            if (categories.isNotEmpty)
              SizedBox(
                height: 38,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    for (final c in categories)
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          label: Text(c),
                          selected: c == _selectedCategory,
                          onSelected: (_) {
                            setState(() => _selectedCategory = c);
                            _load(category: c);
                          },
                          selectedColor: AppColors.primary.withOpacity(0.32),
                          labelStyle: TextStyle(
                            color: c == _selectedCategory
                                ? AppColors.textPrimary
                                : AppColors.textMuted,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                          backgroundColor: AppColors.surfaceElevated,
                          side: BorderSide(
                            color: c == _selectedCategory
                                ? AppColors.accent
                                : AppColors.border,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            const SizedBox(height: 18),
            if (_loading)
              const ShimmerList(itemCount: 5, itemHeight: 110)
            else if (_error != null)
              ErrorCard(message: _error!, onRetry: _load)
            else if (items.isEmpty)
              GlassCard(
                child: Text('No updates in this category yet.',
                    style: TextStyle(color: AppColors.textMuted)),
              )
            else
              ...List.generate(
                items.length,
                (i) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _UpdateCard(item: items[i], index: i),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _UpdateCard extends StatelessWidget {
  final NewsItem item;
  final int index;
  const _UpdateCard({required this.item, required this.index});

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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: color.withOpacity(0.45)),
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
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppColors.border),
                ),
                child: Text(item.category,
                    style: TextStyle(
                        color: AppColors.accent,
                        fontSize: 10,
                        letterSpacing: 1.2,
                        fontWeight: FontWeight.w700)),
              ),
              const Spacer(),
              Text(item.source,
                  style: TextStyle(
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
                style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12.5,
                    height: 1.45)),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              Text(Formatters.date(item.published),
                  style:
                      TextStyle(color: AppColors.textMuted, fontSize: 11)),
              const Spacer(),
              const Icon(Icons.open_in_new_rounded,
                  color: AppColors.accent, size: 14),
              const SizedBox(width: 4),
              Text('Open',
                  style: TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w700,
                      fontSize: 11.5)),
            ],
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (40 * index).ms);
  }
}
