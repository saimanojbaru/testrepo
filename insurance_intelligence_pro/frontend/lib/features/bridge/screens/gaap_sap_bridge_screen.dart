import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/hyperlinked_text.dart';
import '../../../widgets/section_header.dart';
import '../../gamification/services/xp_service.dart';

/// Interactive GAAP-equity → STAT-surplus reconciliation wizard.
///
/// User toggles common reconciling adjustments and sees the running
/// equity/surplus number update in real time. Each adjustment links to
/// the governing SSAP / ASC standard.
class GaapSapBridgeScreen extends StatefulWidget {
  const GaapSapBridgeScreen({super.key});
  @override
  State<GaapSapBridgeScreen> createState() => _GaapSapBridgeScreenState();
}

class _Adjustment {
  final String key;
  final String label;
  final String guidance;
  final double amountUsdMillions; // negative = surplus reduction
  final String description;
  const _Adjustment(
      {required this.key,
      required this.label,
      required this.guidance,
      required this.amountUsdMillions,
      required this.description});
}

class _GaapSapBridgeScreenState extends State<GaapSapBridgeScreen> {
  // Starting GAAP equity (sample) in USD millions.
  static const double _startEquityMm = 32940;

  static const List<_Adjustment> _adjustments = [
    _Adjustment(
      key: 'dac',
      label: 'Non-admit DAC (ASC 944-30 asset → 0)',
      guidance: 'SSAP 4 + SSAP 71 — DAC is non-admitted under SAP.',
      amountUsdMillions: -2850,
      description:
          'GAAP capitalizes incremental, direct acquisition costs and amortizes them on a constant-level basis (ASC 944-30). SAP expenses them when incurred — the DAC asset is fully non-admitted.',
    ),
    _Adjustment(
      key: 'goodwill-cap',
      label: 'Goodwill admission cap (SSAP 68 10%)',
      guidance:
          'SSAP 68 — goodwill admitted to lesser of 10% of capital or amortizable amount.',
      amountUsdMillions: -1120,
      description:
          'GAAP holds business-combination goodwill at carrying value, impairment-tested. SSAP 68 caps admitted goodwill at 10% of subsidiary capital and amortizes over 10 years.',
    ),
    _Adjustment(
      key: 'voba',
      label: 'Write off VOBA',
      guidance: 'ASC 944-340 VOBA vs SSAP 68 — effectively no STAT analog.',
      amountUsdMillions: -640,
      description:
          'Value of Business Acquired is a GAAP-only construct. Under SAP, VOBA is effectively non-admitted (replaced by limited goodwill admission).',
    ),
    _Adjustment(
      key: 'prepaid',
      label: 'Non-admit prepaid expenses (SSAP 29)',
      guidance: 'SSAP 29 — prepaid expenses are non-admitted.',
      amountUsdMillions: -85,
      description:
          'GAAP recognizes prepaid expenses as assets. SAP non-admits them — they cannot be liquidated to pay claims.',
    ),
    _Adjustment(
      key: 'furniture',
      label: 'Non-admit furniture / EDP non-operating (SSAP 19/16R)',
      guidance: 'SSAP 19 + SSAP 16R — non-admitted PP&E and software.',
      amountUsdMillions: -120,
      description:
          'GAAP capitalizes furniture, fixtures, leasehold improvements, and most software. SAP non-admits all of these.',
    ),
    _Adjustment(
      key: 'rou',
      label: 'Non-admit operating-lease ROU asset (SSAP 22R)',
      guidance: 'SSAP 22R — operating-lease ROU is non-admitted.',
      amountUsdMillions: -210,
      description:
          'ASC 842 brought operating leases onto the GAAP balance sheet. SAP recognizes the lease liability but non-admits the ROU asset.',
    ),
    _Adjustment(
      key: 'avr',
      label: 'Subtract AVR contingency reserve (Life)',
      guidance: 'SSAP 26R / NAIC AVR formula.',
      amountUsdMillions: -780,
      description:
          'Life insurers build the Asset Valuation Reserve via formula factors on bond and stock portfolios. AVR reduces surplus on the STAT books.',
    ),
    _Adjustment(
      key: 'imr',
      label: 'Subtract IMR (Life — deferred rate gains)',
      guidance: 'NAIC IMR — defers interest-rate-driven realized gains.',
      amountUsdMillions: -240,
      description:
          'The Interest Maintenance Reserve defers rate-driven realized gains/losses on bonds and amortizes them back to income over the remaining life of the sold bond. IMR is held as a liability and reduces surplus.',
    ),
    _Adjustment(
      key: 'bond-mark',
      label: 'Reverse AFS OCI (bonds at amortized cost in SAP)',
      guidance: 'SSAP 26R — NAIC 1-2 bonds at amortized cost.',
      amountUsdMillions: 1120,
      description:
          'GAAP carries AFS bonds at fair value with unrealized changes through OCI. SAP carries NAIC 1-2 bonds at amortized cost — reverse the AOCI bond mark to bridge.',
    ),
    _Adjustment(
      key: 'dta',
      label: 'Limit DTA admission (SSAP 101 three-bucket test)',
      guidance: 'SSAP 101 — DTA admission via three-bucket test.',
      amountUsdMillions: -350,
      description:
          'GAAP recognizes DTAs net of valuation allowance based on more-likely-than-not. SAP admits DTAs only to the extent they pass the SSAP 101 three-bucket test.',
    ),
    _Adjustment(
      key: 'surplus-notes',
      label: 'Add surplus-note balance (SSAP 41R)',
      guidance: 'SSAP 41R — surplus notes count as surplus, not debt.',
      amountUsdMillions: 500,
      description:
          'GAAP classifies a surplus note as long-term debt. SAP includes it in surplus until commissioner-approved interest is paid.',
    ),
  ];

  final Set<String> _on = <String>{};
  int _bridgeCount = 0;

  double get _running {
    var total = _startEquityMm;
    for (final a in _adjustments) {
      if (_on.contains(a.key)) total += a.amountUsdMillions;
    }
    return total;
  }

  Future<void> _toggle(String key) async {
    setState(() {
      if (_on.contains(key)) {
        _on.remove(key);
      } else {
        _on.add(key);
        _bridgeCount += 1;
        XpService.instance.recordEvent('bridge_adjustment', xpDelta: 5);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final running = _running;
    final delta = running - _startEquityMm;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('GAAP ↔ SAP Bridge',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          _HeaderCard(
              gaap: _startEquityMm, surplus: running, delta: delta),
          const SizedBox(height: 18),
          const SectionHeader(
              title: 'Reconciling adjustments',
              subtitle:
                  'Toggle each adjustment to update the running surplus'),
          for (var i = 0; i < _adjustments.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _AdjustmentRow(
                  adj: _adjustments[i],
                  on: _on.contains(_adjustments[i].key),
                  onToggle: () => _toggle(_adjustments[i].key),
                  index: i),
            ),
          if (_bridgeCount >= 10) ...[
            const SizedBox(height: 10),
            GlassCard(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFFB877FF), Color(0xFF1F4FFF)],
              ),
              child: const Row(
                children: [
                  Icon(Icons.workspace_premium,
                      color: Colors.white, size: 28),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'SAP Slayer — you\'ve logged 10 bridge adjustments. Badge unlocked in your Trophy Case.',
                      style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w700,
                          height: 1.4),
                    ),
                  ),
                ],
              ),
            ).animate().fadeIn(),
          ]
        ],
      ),
    );
  }
}

class _HeaderCard extends StatelessWidget {
  final double gaap;
  final double surplus;
  final double delta;
  const _HeaderCard(
      {required this.gaap, required this.surplus, required this.delta});

  String _money(double n) {
    if (n.abs() >= 1000) return '\$${(n / 1000).toStringAsFixed(2)}B';
    return '\$${n.toStringAsFixed(0)}M';
  }

  @override
  Widget build(BuildContext context) {
    final deltaColor =
        delta >= 0 ? AppColors.positive : AppColors.negative;
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF18284A), Color(0xFF0B1426)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('SAMPLE CARRIER · METLIFE (MET) FY2024',
              style: TextStyle(
                  color: AppColors.accent,
                  fontSize: 10,
                  letterSpacing: 1.4,
                  fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('GAAP equity',
                        style: TextStyle(
                            color: AppColors.textMuted, fontSize: 11)),
                    Text(_money(gaap),
                        style: const TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.w800,
                            fontSize: 22,
                            letterSpacing: -0.4)),
                  ],
                ),
              ),
              const Icon(Icons.arrow_forward_rounded,
                  color: AppColors.accent),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text('STAT surplus (est.)',
                        style: TextStyle(
                            color: AppColors.textMuted, fontSize: 11)),
                    Text(_money(surplus),
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontWeight: FontWeight.w800,
                            fontSize: 22,
                            letterSpacing: -0.4)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(
                horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: deltaColor.withValues(alpha: 0.16),
              borderRadius: BorderRadius.circular(8),
              border:
                  Border.all(color: deltaColor.withValues(alpha: 0.5)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                    delta >= 0
                        ? Icons.trending_up_rounded
                        : Icons.trending_down_rounded,
                    color: deltaColor,
                    size: 14),
                const SizedBox(width: 6),
                Text(
                    '${delta >= 0 ? '+' : ''}${_money(delta)}  applied',
                    style: TextStyle(
                        color: deltaColor,
                        fontWeight: FontWeight.w800,
                        fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 320.ms);
  }
}

class _AdjustmentRow extends StatelessWidget {
  final _Adjustment adj;
  final bool on;
  final VoidCallback onToggle;
  final int index;
  const _AdjustmentRow(
      {required this.adj,
      required this.on,
      required this.onToggle,
      required this.index});

  String _money(double n) {
    final s = n >= 0 ? '+' : '−';
    final v = n.abs();
    if (v >= 1000) return '$s\$${(v / 1000).toStringAsFixed(2)}B';
    return '$s\$${v.toStringAsFixed(0)}M';
  }

  @override
  Widget build(BuildContext context) {
    final negative = adj.amountUsdMillions < 0;
    final color = negative ? AppColors.negative : AppColors.positive;
    return GlassCard(
      onTap: onToggle,
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      borderColor:
          on ? AppColors.accent.withValues(alpha: 0.5) : AppColors.border,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  color: on ? AppColors.accent : Colors.transparent,
                  border: Border.all(
                      color: on ? AppColors.accent : AppColors.border),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: on
                    ? const Icon(Icons.check,
                        color: Colors.white, size: 14)
                    : null,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(adj.label,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 13)),
              ),
              const SizedBox(width: 8),
              Text(_money(adj.amountUsdMillions),
                  style: TextStyle(
                      color: color,
                      fontWeight: FontWeight.w800,
                      fontSize: 13)),
            ],
          ),
          const SizedBox(height: 6),
          HyperlinkedText(adj.description,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 11.5,
                  height: 1.45)),
          const SizedBox(height: 4),
          HyperlinkedText(adj.guidance,
              style: const TextStyle(
                  color: AppColors.accent,
                  fontSize: 10.5,
                  fontWeight: FontWeight.w700)),
        ],
      ),
    ).animate().fadeIn(duration: 240.ms, delay: (20 * index).ms);
  }
}
