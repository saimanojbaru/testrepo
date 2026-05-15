import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/section_header.dart';
import '../../gamification/services/xp_service.dart';

/// Reserve Analysis Toolkit:
///   • Loss-development triangle viewer (mock — Progressive auto book)
///   • Combined-ratio / Loss-ratio / Expense-ratio calculator
///   • Reserve-adequacy flag heuristic
class ReserveToolkitScreen extends StatefulWidget {
  const ReserveToolkitScreen({super.key});
  @override
  State<ReserveToolkitScreen> createState() => _ReserveToolkitScreenState();
}

class _ReserveToolkitScreenState extends State<ReserveToolkitScreen>
    with TickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);
  bool _triangleViewed = false;

  // Mock paid-loss triangle — Progressive personal auto FY2020-2024 (USD M).
  static const List<List<double>> _paidTriangle = [
    // AY ↓ / Development age 12, 24, 36, 48, 60 months
    [12400, 16800, 17600, 18000, 18100], // AY 2020
    [14300, 19100, 20300, 20700],         // AY 2021
    [17600, 23400, 24800],                // AY 2022
    [19800, 26300],                       // AY 2023
    [22400],                              // AY 2024
  ];
  static const _years = [2020, 2021, 2022, 2023, 2024];
  static const _devAges = [12, 24, 36, 48, 60];

  // Ratio calculator inputs
  final _incurredCtrl = TextEditingController(text: '47200');
  final _expenseCtrl = TextEditingController(text: '8810');
  final _premiumCtrl = TextEditingController(text: '71477');

  @override
  void dispose() {
    _tabs.dispose();
    _incurredCtrl.dispose();
    _expenseCtrl.dispose();
    _premiumCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Reserve Toolkit',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: AppColors.accent,
          labelColor: AppColors.accent,
          unselectedLabelColor: AppColors.textMuted,
          labelStyle: const TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 12,
              letterSpacing: 0.4),
          tabs: const [
            Tab(text: 'Triangle'),
            Tab(text: 'Ratios'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _TriangleTab(
            triangle: _paidTriangle,
            years: _years,
            ages: _devAges,
            onViewed: _onTriangleViewed,
          ),
          _RatiosTab(
            incurredCtrl: _incurredCtrl,
            expenseCtrl: _expenseCtrl,
            premiumCtrl: _premiumCtrl,
          ),
        ],
      ),
    );
  }

  void _onTriangleViewed() async {
    if (_triangleViewed) return;
    _triangleViewed = true;
    await XpService.instance.recordEvent('triangle_viewed', xpDelta: 20);
  }
}

class _TriangleTab extends StatelessWidget {
  final List<List<double>> triangle;
  final List<int> years;
  final List<int> ages;
  final VoidCallback onViewed;
  const _TriangleTab(
      {required this.triangle,
      required this.years,
      required this.ages,
      required this.onViewed});

  // Develop the triangle to ultimate using simple chain-ladder ratios.
  Map<int, double> _projectUltimates() {
    final factors = <double>[];
    for (var c = 0; c < ages.length - 1; c++) {
      double num = 0, den = 0;
      for (var r = 0; r < triangle.length; r++) {
        if (c + 1 < triangle[r].length) {
          num += triangle[r][c + 1];
          den += triangle[r][c];
        }
      }
      factors.add(den == 0 ? 1 : num / den);
    }
    final ult = <int, double>{};
    for (var r = 0; r < triangle.length; r++) {
      final row = triangle[r];
      double v = row.last;
      for (var c = row.length - 1; c < ages.length - 1; c++) {
        v *= factors[c];
      }
      ult[years[r]] = v;
    }
    return ult;
  }

  @override
  Widget build(BuildContext context) {
    // Schedule the XP event after first frame so we don't fire during build.
    WidgetsBinding.instance.addPostFrameCallback((_) => onViewed());
    final ultimates = _projectUltimates();
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 36),
      children: [
        const Text(
          'Progressive Corp. (PGR) — Private-passenger auto liability paid-loss triangle (USD M, mock).',
          style: TextStyle(
              color: AppColors.textMuted, fontSize: 12, height: 1.45),
        ),
        const SizedBox(height: 10),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: GlassCard(
            padding: const EdgeInsets.all(12),
            child: DataTable(
              columnSpacing: 18,
              headingRowHeight: 32,
              dataRowMinHeight: 36,
              dataRowMaxHeight: 36,
              headingTextStyle: const TextStyle(
                  color: AppColors.accent,
                  fontWeight: FontWeight.w800,
                  fontSize: 11,
                  letterSpacing: 0.6),
              dataTextStyle: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600),
              columns: [
                const DataColumn(label: Text('AY')),
                for (final a in ages) DataColumn(label: Text('${a}m')),
              ],
              rows: [
                for (var r = 0; r < triangle.length; r++)
                  DataRow(cells: [
                    DataCell(Text('${years[r]}',
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontWeight: FontWeight.w800))),
                    for (var c = 0; c < ages.length; c++)
                      DataCell(c < triangle[r].length
                          ? Text(triangle[r][c].toStringAsFixed(0))
                          : const Text('—',
                              style: TextStyle(color: AppColors.textMuted))),
                  ]),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        const SectionHeader(title: 'Chain-ladder projected ultimate'),
        GlassCard(
          child: Column(
            children: [
              for (final e in ultimates.entries)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 60,
                        child: Text('AY ${e.key}',
                            style: const TextStyle(
                                color: AppColors.accent,
                                fontWeight: FontWeight.w800,
                                fontSize: 12)),
                      ),
                      Expanded(
                        child: LayoutBuilder(builder: (context, c) {
                          final maxV = ultimates.values
                              .reduce((a, b) => a > b ? a : b);
                          final w = c.maxWidth * (e.value / maxV);
                          return Stack(children: [
                            Container(
                              height: 8,
                              decoration: BoxDecoration(
                                color: AppColors.surfaceHigh,
                                borderRadius: BorderRadius.circular(4),
                              ),
                            ),
                            Container(
                              width: w,
                              height: 8,
                              decoration: BoxDecoration(
                                gradient: AppColors.primaryGradient,
                                borderRadius: BorderRadius.circular(4),
                              ),
                            ),
                          ]);
                        }),
                      ),
                      const SizedBox(width: 10),
                      SizedBox(
                        width: 76,
                        child: Text(
                            '\$${(e.value / 1000).toStringAsFixed(2)}B',
                            textAlign: TextAlign.right,
                            style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontWeight: FontWeight.w700,
                                fontSize: 12)),
                      )
                    ],
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        GlassCard(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF18284A), Color(0xFF0B1426)],
          ),
          child: const Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.info_outline,
                  color: AppColors.accent, size: 18),
              SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Methodology: simple chain-ladder development on this 5×5 paid triangle. '
                  'For audit work, blend with Bornhuetter-Ferguson and an expected-loss-ratio method, then assess the actuarial central estimate vs management\'s booked reserve.',
                  style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 12,
                      height: 1.5),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _RatiosTab extends StatefulWidget {
  final TextEditingController incurredCtrl;
  final TextEditingController expenseCtrl;
  final TextEditingController premiumCtrl;
  const _RatiosTab(
      {required this.incurredCtrl,
      required this.expenseCtrl,
      required this.premiumCtrl});

  @override
  State<_RatiosTab> createState() => _RatiosTabState();
}

class _RatiosTabState extends State<_RatiosTab> {
  double _toD(TextEditingController c) =>
      double.tryParse(c.text.replaceAll(',', '')) ?? 0;

  @override
  Widget build(BuildContext context) {
    final premium = _toD(widget.premiumCtrl);
    final loss = _toD(widget.incurredCtrl);
    final expense = _toD(widget.expenseCtrl);
    final double lossR = premium == 0 ? 0.0 : loss / premium * 100;
    final double expR = premium == 0 ? 0.0 : expense / premium * 100;
    final double combined = lossR + expR;
    final double uwProfit = premium * (1 - combined / 100);
    String adequacy;
    Color adequacyColor;
    if (combined <= 95) {
      adequacy = 'Strong underwriting';
      adequacyColor = AppColors.positive;
    } else if (combined <= 100) {
      adequacy = 'Profitable but tight';
      adequacyColor = AppColors.accent;
    } else if (combined <= 105) {
      adequacy = 'Underwriting losses — relying on investment income';
      adequacyColor = AppColors.warning;
    } else {
      adequacy = 'Underwriting loss — flag for adequacy review';
      adequacyColor = AppColors.negative;
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 36),
      children: [
        const Text(
          'Enter premium, incurred losses, and underwriting expenses to compute the combined ratio and a reserve-adequacy heuristic.',
          style: TextStyle(
              color: AppColors.textMuted, fontSize: 12, height: 1.4),
        ),
        const SizedBox(height: 12),
        _input('Earned premium (\$M)', widget.premiumCtrl),
        _input('Incurred losses & LAE (\$M)', widget.incurredCtrl),
        _input('Underwriting expenses (\$M)', widget.expenseCtrl),
        const SizedBox(height: 18),
        const SectionHeader(title: 'Computed ratios'),
        _ratio('Loss ratio', lossR, suffix: '%'),
        _ratio('Expense ratio', expR, suffix: '%'),
        _ratio('Combined ratio', combined,
            suffix: '%', accent: true),
        _ratio('Underwriting profit', uwProfit, suffix: 'M', dollar: true),
        const SizedBox(height: 14),
        GlassCard(
          borderColor: adequacyColor.withValues(alpha: 0.5),
          child: Row(
            children: [
              Icon(Icons.flag_rounded, color: adequacyColor),
              const SizedBox(width: 12),
              Expanded(
                child: Text(adequacy,
                    style: TextStyle(
                        color: adequacyColor,
                        fontWeight: FontWeight.w800,
                        fontSize: 13)),
              ),
            ],
          ),
        ).animate().fadeIn(duration: 240.ms),
      ],
    );
  }

  Widget _input(String label, TextEditingController c) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: TextField(
        controller: c,
        keyboardType:
            const TextInputType.numberWithOptions(decimal: true),
        onChanged: (_) => setState(() {}),
        style: const TextStyle(
            color: AppColors.textPrimary, fontSize: 13),
        decoration: InputDecoration(
          isDense: true,
          labelText: label,
        ),
      ),
    );
  }

  Widget _ratio(String label, double value,
      {String suffix = '', bool dollar = false, bool accent = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: GlassCard(
        padding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        borderColor: accent
            ? AppColors.accent.withValues(alpha: 0.5)
            : AppColors.border,
        child: Row(
          children: [
            Expanded(
              child: Text(label,
                  style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600)),
            ),
            Text(
                dollar
                    ? '\$${value.toStringAsFixed(0)}$suffix'
                    : '${value.toStringAsFixed(1)}$suffix',
                style: TextStyle(
                    color: accent
                        ? AppColors.accent
                        : AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: accent ? 18 : 15)),
          ],
        ),
      ),
    );
  }
}
