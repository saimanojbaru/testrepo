import '../models/insight.dart';
import '../models/kpi.dart';

/// Dart port of the Python `insight_engine.py`.
///
/// Conclusion-first rule-based insights — same playbook as the
/// backend, runs entirely on-device.
class InsightEngine {
  InsightEngine._();

  static List<Insight> forCompany({
    required KpiSet kpis,
    required List<Map<String, dynamic>> history,
    required String insurerType,
  }) {
    if (history.isEmpty) {
      return [
        Insight(
          title: 'Limited financial coverage',
          detail: 'We could not extract enough data to form insights.',
          level: 'neutral',
          icon: 'info',
          tags: const [],
        ),
      ];
    }
    final out = <Insight>[
      ..._underwriting(kpis, insurerType),
      ..._growth(kpis),
      ..._balanceSheet(history),
      ..._investment(kpis),
      ..._profitability(history, kpis),
    ];
    return out.take(8).toList();
  }

  static Kpi? _kpi(KpiSet set, String code) {
    for (final k in [...set.primary, ...set.secondary]) {
      if (k.code == code) return k;
    }
    return null;
  }

  static List<Insight> _underwriting(KpiSet kpis, String type) {
    final out = <Insight>[];
    final cr = _kpi(kpis, 'combined_ratio');
    if (cr?.value != null) {
      final v = cr!.value!;
      if (v < 95) {
        out.add(Insight(
          title: 'Strong underwriting discipline',
          detail:
              'Combined ratio of ${v.toStringAsFixed(1)}% indicates a solid underwriting profit.',
          level: 'positive',
          icon: 'shield',
          tags: const ['underwriting'],
        ));
      } else if (v < 100) {
        out.add(Insight(
          title: 'Underwriting at breakeven',
          detail:
              'Combined ratio of ${v.toStringAsFixed(1)}% is profitable but tight.',
          level: 'neutral',
          icon: 'balance',
          tags: const ['underwriting'],
        ));
      } else {
        out.add(Insight(
          title: 'Underwriting losses persisting',
          detail:
              'Combined ratio of ${v.toStringAsFixed(1)}% means losses + expenses exceed premium; profit relies on investment income.',
          level: 'negative',
          icon: 'alert',
          tags: const ['underwriting'],
        ));
      }
      if ((cr.deltaYoy ?? 0) > 1.5) {
        out.add(Insight(
          title: 'Combined ratio deteriorating',
          detail:
              'YoY +${cr.deltaYoy!.toStringAsFixed(1)}% — claims inflation and severity pressure visible.',
          level: 'warning',
          icon: 'trend-up',
          tags: const ['claims'],
        ));
      }
    }
    final mlr = _kpi(kpis, 'medical_loss_ratio');
    if (mlr?.value != null && type == 'Health' && mlr!.value! > 88) {
      out.add(Insight(
        title: 'MLR pressure',
        detail:
            'Medical loss ratio of ${mlr.value!.toStringAsFixed(1)}% leaves limited margin for admin and profit.',
        level: 'warning',
        icon: 'medical',
        tags: const ['MLR'],
      ));
    }
    return out;
  }

  static List<Insight> _growth(KpiSet kpis) {
    final g = _kpi(kpis, 'premium_growth');
    if (g?.value == null) return const [];
    final v = g!.value!;
    if (v > 10) {
      return [
        Insight(
          title: 'Premium scaling rapidly',
          detail:
              'Premium growth +${v.toStringAsFixed(1)}% YoY — watch for reserve adequacy on newer cohorts.',
          level: 'positive',
          icon: 'rocket',
          tags: const ['growth'],
        ),
      ];
    }
    if (v > 4) {
      return [
        Insight(
          title: 'Healthy top-line momentum',
          detail:
              'Premium growth +${v.toStringAsFixed(1)}% YoY — above industry average.',
          level: 'positive',
          icon: 'trend-up',
          tags: const ['growth'],
        ),
      ];
    }
    if (v < 0) {
      return [
        Insight(
          title: 'Top-line contraction',
          detail:
              'Premium ${v.toStringAsFixed(1)}% YoY — pricing or retention may be under pressure.',
          level: 'warning',
          icon: 'trend-down',
          tags: const ['growth'],
        ),
      ];
    }
    return const [];
  }

  static List<Insight> _balanceSheet(List<Map<String, dynamic>> history) {
    if (history.length < 2) return const [];
    final latest = history.last;
    final prev = history[history.length - 2];
    final out = <Insight>[];
    final resNow = (latest['reserves'] as num?)?.toDouble();
    final resPrev = (prev['reserves'] as num?)?.toDouble();
    final eqNow = (latest['equity'] as num?)?.toDouble();
    if (resNow != null && resPrev != null && resPrev > 0 && resNow > 0) {
      final delta = (resNow - resPrev) / resPrev * 100;
      if (delta > 8) {
        out.add(Insight(
          title: 'Reserves expanding rapidly',
          detail:
              'Reserves +${delta.toStringAsFixed(1)}% YoY — supports growth but tightens RBC headroom.',
          level: 'neutral',
          icon: 'vault',
          tags: const ['reserves'],
        ));
      } else if (delta < -3) {
        out.add(Insight(
          title: 'Reserve releases visible',
          detail:
              'Reserves ${delta.toStringAsFixed(1)}% YoY — favorable development is boosting earnings quality.',
          level: 'positive',
          icon: 'vault',
          tags: const ['reserves'],
        ));
      }
    }
    if (eqNow != null && resNow != null && eqNow > 0 && resNow / eqNow > 6) {
      out.add(Insight(
        title: 'Elevated reserve leverage',
        detail:
            'Reserves are ${(resNow / eqNow).toStringAsFixed(1)}× equity — capital sensitivity is high.',
        level: 'warning',
        icon: 'warning',
        tags: const ['leverage'],
      ));
    }
    return out;
  }

  static List<Insight> _investment(KpiSet kpis) {
    final inv = _kpi(kpis, 'investment_yield');
    if (inv?.value == null) return const [];
    final v = inv!.value!;
    if ((inv.deltaYoy ?? 0) > 5) {
      return [
        Insight(
          title: 'Investment income tailwind',
          detail:
              'Yield up ${inv.deltaYoy!.toStringAsFixed(1)}% YoY as the portfolio reprices into a higher-rate regime.',
          level: 'positive',
          icon: 'coins',
          tags: const ['investment'],
        ),
      ];
    }
    if (v < 3.0) {
      return [
        Insight(
          title: 'Investment yield drag',
          detail:
              'Yield of ${v.toStringAsFixed(2)}% trails industry; portfolio mix may be defensive.',
          level: 'warning',
          icon: 'coins',
          tags: const ['investment'],
        ),
      ];
    }
    return const [];
  }

  static List<Insight> _profitability(
      List<Map<String, dynamic>> history, KpiSet kpis) {
    if (history.length < 2) return const [];
    final latest = history.last;
    final prev = history[history.length - 2];
    final niNow = (latest['net_income'] as num?)?.toDouble() ?? 0;
    final niPrev = (prev['net_income'] as num?)?.toDouble() ?? 0;
    final growth = _kpi(kpis, 'premium_growth');
    if (niNow < 0) {
      return [
        Insight(
          title: 'Net loss for fiscal year',
          detail:
              'Bottom line in the red; investigate large CAT, reserve charges, or DAC unlocking.',
          level: 'negative',
          icon: 'trend-down',
          tags: const ['profitability'],
        ),
      ];
    }
    if (niPrev > 0 && niNow < niPrev * 0.7) {
      final note = (growth?.value ?? 0) > 0
          ? 'Net income fell to \$${niNow.toStringAsFixed(0)}M from \$${niPrev.toStringAsFixed(0)}M — margin pressure despite growth.'
          : 'Net income fell to \$${niNow.toStringAsFixed(0)}M from \$${niPrev.toStringAsFixed(0)}M.';
      return [
        Insight(
          title: 'Earnings compression',
          detail: note,
          level: 'warning',
          icon: 'trend-down',
          tags: const ['earnings'],
        ),
      ];
    }
    if (niPrev > 0 && niNow > niPrev * 1.25) {
      final pct = ((niNow / niPrev - 1) * 100).toStringAsFixed(1);
      return [
        Insight(
          title: 'Earnings rebound',
          detail:
              'Net income up $pct% YoY on improved underwriting and yields.',
          level: 'positive',
          icon: 'trend-up',
          tags: const ['earnings'],
        ),
      ];
    }
    return const [];
  }

  // ---- Dashboard / industry-wide -------------------------------------------

  static const List<Insight> industryPulse = [
    Insight(
      title: 'Combined ratios rising across P&C',
      detail:
          'Sector-wide CR drift +2.1% as social inflation and severe weather extend the hard market.',
      level: 'warning',
      icon: 'trend-up',
      tags: ['P&C', 'industry'],
    ),
    Insight(
      title: 'Investment yields lifting Life carriers',
      detail:
          'Net investment income at multi-year highs as portfolios reprice — cushioning DAC unlocking.',
      level: 'positive',
      icon: 'coins',
      tags: ['Life', 'yields'],
    ),
    Insight(
      title: 'LDTI noise normalising in 2025 filings',
      detail:
          'Cohort-level LFPB rollforwards now disclosed by all SEC life filers; comparability improving.',
      level: 'positive',
      icon: 'standard',
      tags: ['LDTI', 'GAAP'],
    ),
    Insight(
      title: 'Reinsurance capacity selectively returning',
      detail:
          'Property cat retro pricing softening at mid-year; primary insurers retaining more risk.',
      level: 'neutral',
      icon: 'globe',
      tags: ['reinsurance'],
    ),
    Insight(
      title: 'Health MLRs holding above 87%',
      detail:
          'Utilization runs hot post-COVID backlog; ACA rebates expected for several MA carriers.',
      level: 'warning',
      icon: 'medical',
      tags: ['Health', 'MLR'],
    ),
  ];

  static const List<Map<String, dynamic>> industryIndicators = [
    {'label': '10Y Treasury', 'value': '4.32%', 'delta': 0.04, 'direction': 'up'},
    {'label': 'Claims Inflation (P&C)', 'value': '+5.8%', 'delta': 0.6, 'direction': 'up'},
    {'label': 'Auto Severity YoY', 'value': '+8.4%', 'delta': 1.2, 'direction': 'up'},
    {'label': 'Cat Bond Issuance', 'value': '\$12.1B', 'delta': 0.18, 'direction': 'up'},
    {'label': 'MA Star Ratings (4+)', 'value': '42%', 'delta': -0.05, 'direction': 'down'},
  ];

  static const List<List<double>> sparklines = [
    [96.1, 97.4, 98.6, 99.2, 100.8, 99.5, 98.7],
    [3.4, 3.6, 3.9, 4.0, 4.1, 4.2, 4.3],
    [85.0, 85.6, 86.2, 86.8, 87.1, 87.4, 87.6],
  ];

  static const List<Map<String, dynamic>> headlines = [
    {
      'title': 'NAIC adopts updated AI Model Bulletin',
      'source': 'NAIC',
      'category': 'Regulation',
      'impact': 'High',
      'published': '2026-04-22',
      'summary':
          'Updated guidance addresses governance, testing, and bias monitoring for AI used in underwriting and claims.',
      'url': 'https://content.naic.org/'
    },
    {
      'title': 'P&C Combined Ratio expected to remain above 99% in 2026',
      'source': 'Insurance Journal',
      'category': 'Earnings',
      'impact': 'Medium',
      'published': '2026-04-18',
      'summary':
          'Analysts cite social inflation and reinsurance pricing as key headwinds despite hard-market premium growth.',
      'url': 'https://www.insurancejournal.com/'
    },
    {
      'title': 'Cat bond issuance hits record high heading into wind season',
      'source': 'Artemis',
      'category': 'Catastrophe',
      'impact': 'Medium',
      'published': '2026-04-15',
      'summary':
          'Total ILS market exceeds \$50bn outstanding as investor appetite stays strong.',
      'url': 'https://www.artemis.bm/'
    },
    {
      'title': 'LDTI disclosures show wider divergence in life cohort margins',
      'source': 'SEC Press',
      'category': 'Regulation',
      'impact': 'High',
      'published': '2026-04-10',
      'summary':
          'Second-year LDTI filings reveal meaningful spread in cohort-level locked-in profitability.',
      'url': 'https://www.sec.gov/'
    },
    {
      'title': 'Reinsurance retro pricing softens at mid-year renewals',
      'source': 'Reinsurance News',
      'category': 'Reinsurance',
      'impact': 'Medium',
      'published': '2026-04-08',
      'summary':
          'Capacity increases and benign Q1 cat losses ease retro rates by 5-10%.',
      'url': 'https://www.reinsurancene.ws/'
    },
    {
      'title': 'Cyber market continues hardening cycle',
      'source': 'Insurance Journal',
      'category': 'Cyber',
      'impact': 'Medium',
      'published': '2026-04-04',
      'summary':
          'Ransomware frequency rebounds; insurers tighten retention and exclusions.',
      'url': 'https://www.insurancejournal.com/'
    },
    {
      'title': 'Auto severity continues to outpace frequency in private auto',
      'source': 'Insurance Journal',
      'category': 'Earnings',
      'impact': 'Medium',
      'published': '2026-03-29',
      'summary':
          'Repair, medical, and litigation costs lift severity into the high single digits even as frequency moderates.',
      'url': 'https://www.insurancejournal.com/'
    },
    {
      'title': 'Florida reform shows early signs of stabilising the market',
      'source': 'AM Best',
      'category': 'Regulation',
      'impact': 'High',
      'published': '2026-03-22',
      'summary':
          'New entrants file rate plans as litigation reform reduces assignment-of-benefits abuse.',
      'url': 'https://news.ambest.com/'
    },
    {
      'title': 'Medicare Advantage profitability under fresh scrutiny',
      'source': 'Insurance Journal',
      'category': 'Health',
      'impact': 'High',
      'published': '2026-03-18',
      'summary':
          'CMS proposed rate notice and v28 risk-adjustment phase-in continue to compress MA margins.',
      'url': 'https://www.insurancejournal.com/'
    },
    {
      'title': 'Cyber cat bond issuance breaks new ground',
      'source': 'Artemis',
      'category': 'Catastrophe',
      'impact': 'Medium',
      'published': '2026-03-12',
      'summary':
          'Several primary cyber insurers complete inaugural ILS placements covering systemic events.',
      'url': 'https://www.artemis.bm/'
    },
    {
      'title': 'NAIC sets new RBC charges for crypto-linked exposures',
      'source': 'NAIC',
      'category': 'Regulation',
      'impact': 'Medium',
      'published': '2026-03-05',
      'summary':
          'Higher capital factors take effect for digital-asset and tokenised-bond holdings starting year-end.',
      'url': 'https://content.naic.org/'
    },
    {
      'title': 'Wildfire models recalibrated for 2026 wind season',
      'source': 'Reinsurance News',
      'category': 'Catastrophe',
      'impact': 'Medium',
      'published': '2026-02-28',
      'summary':
          'Vendor wildfire models incorporate updated drought and vegetation maps, shifting expected loss views.',
      'url': 'https://www.reinsurancene.ws/'
    },
  ];

  static const String pulseHeadline =
      'P&C combined ratios still elevated; Life carriers benefit from yield tailwind.';
}
