import '../models/kpi.dart';

/// Dart port of the Python `kpi_engine.py`.
///
/// Computes insurance-specific KPIs, status banding, and risk-radar
/// factors directly from a fiscal history list. Each KPI is annotated
/// with formula provenance, source-filing metadata, and a knowledge-tab
/// slug so the drill-down screen can show "what does this mean".
class KpiEngine {
  KpiEngine._();

  static KpiSet compute({
    required String insurerType,
    required List<Map<String, dynamic>> history,
    required Map<String, dynamic> benchmarks,
    String? ticker,
  }) {
    if (history.isEmpty) {
      return KpiSet(insurerType: insurerType, primary: const [], secondary: const []);
    }
    final latest = history.last;
    final prev = history.length >= 2 ? history[history.length - 2] : <String, dynamic>{};
    final bench = (benchmarks[insurerType] as Map?) ?? <String, dynamic>{};
    final fy = (latest['fy'] as num?)?.toInt();
    final source = _filingSource(fy, ticker);

    if (insurerType == 'Life') return _attachSource(_life(latest, prev, bench), source, latest, prev);
    if (insurerType == 'Health') return _attachSource(_health(latest, prev, bench), source, latest, prev);
    return _attachSource(_pc(latest, prev, bench), source, latest, prev);
  }

  /// Build the synthetic filing source for FY annual snapshots.
  ///
  /// US insurers with calendar fiscal years file their 10-K in
  /// February–April of the following year. The placeholder accession
  /// is replaced when the optional online backend serves real EDGAR
  /// metadata.
  static FilingSource? _filingSource(int? fy, String? ticker) {
    if (fy == null) return null;
    return FilingSource(
      form: '10-K',
      fiscalYear: 'FY$fy',
      filedDate: 'Q1 ${fy + 1}',
      url: ticker == null
          ? 'https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&type=10-K'
          : 'https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=$ticker&type=10-K',
    );
  }

  /// Walk the produced KpiSet and stamp `source` + `formula` +
  /// `methodology` + `knowledgeSlug` onto each KPI.  Done after the
  /// per-type branches so we don't have to thread those into every
  /// KPI builder call.
  static KpiSet _attachSource(
    KpiSet set,
    FilingSource? source,
    Map latest,
    Map prev,
  ) {
    Kpi enrich(Kpi k) {
      final f = _formulaFor(k.code, latest, prev);
      return Kpi(
        code: k.code,
        label: k.label,
        value: k.value,
        unit: k.unit,
        direction: k.direction,
        deltaYoy: k.deltaYoy,
        benchmark: k.benchmark,
        status: k.status,
        description: k.description,
        source: source,
        formula: f,
        methodology: _methodologyFor(k.code),
        knowledgeSlug: _slugFor(k.code),
      );
    }

    return KpiSet(
      insurerType: set.insurerType,
      primary: set.primary.map(enrich).toList(),
      secondary: set.secondary.map(enrich).toList(),
    );
  }

  static KpiFormula? _formulaFor(String code, Map latest, Map prev) {
    String fmt(dynamic v) {
      if (v == null) return '–';
      if (v is num) return '\$${v.toStringAsFixed(0)}M';
      return v.toString();
    }

    switch (code) {
      case 'loss_ratio':
        return KpiFormula(
          expression: 'Incurred Losses / Earned Premium × 100',
          numerator: 'Incurred Losses & LAE',
          denominator: 'Earned Premium',
          numeratorValue: fmt(latest['losses']),
          denominatorValue: fmt(latest['premiums']),
        );
      case 'expense_ratio':
        return KpiFormula(
          expression: 'Underwriting Expenses / Earned Premium × 100',
          numerator: 'Underwriting Expenses',
          denominator: 'Earned Premium',
          numeratorValue: fmt(latest['expenses']),
          denominatorValue: fmt(latest['premiums']),
        );
      case 'combined_ratio':
        return const KpiFormula(
          expression: 'Loss Ratio + Expense Ratio',
          numerator: 'Loss Ratio',
          denominator: 'Expense Ratio',
          numeratorValue: 'computed above',
          denominatorValue: 'computed above',
        );
      case 'underwriting_profit':
        return KpiFormula(
          expression: 'Earned Premium × (1 − Combined Ratio)',
          numerator: 'Earned Premium',
          denominator: 'Combined Ratio',
          numeratorValue: fmt(latest['premiums']),
          denominatorValue: 'computed above',
        );
      case 'investment_yield':
        return KpiFormula(
          expression: 'Net Investment Income / Reserves × 100',
          numerator: 'Net Investment Income',
          denominator: 'Policy / Loss Reserves',
          numeratorValue: fmt(latest['investment_income']),
          denominatorValue: fmt(latest['reserves']),
        );
      case 'premium_growth':
        return KpiFormula(
          expression: '(Earned Premium FYₜ − FYₜ₋₁) / FYₜ₋₁ × 100',
          numerator: 'Earned Premium (current FY)',
          denominator: 'Earned Premium (prior FY)',
          numeratorValue: fmt(latest['premiums']),
          denominatorValue: fmt(prev['premiums']),
        );
      case 'leverage':
        return KpiFormula(
          expression: 'Reserves / Equity',
          numerator: 'Policy / Loss Reserves',
          denominator: 'Stockholders\' Equity',
          numeratorValue: fmt(latest['reserves']),
          denominatorValue: fmt(latest['equity']),
        );
      case 'roe':
        return KpiFormula(
          expression: 'Net Income / Stockholders\' Equity × 100',
          numerator: 'Net Income',
          denominator: 'Stockholders\' Equity',
          numeratorValue: fmt(latest['net_income']),
          denominatorValue: fmt(latest['equity']),
        );
      case 'medical_loss_ratio':
        return KpiFormula(
          expression: 'Medical Claims Incurred / Earned Premium × 100',
          numerator: 'Medical Claims Incurred',
          denominator: 'Earned Premium',
          numeratorValue: fmt(latest['losses']),
          denominatorValue: fmt(latest['premiums']),
        );
      case 'persistency':
        return KpiFormula(
          expression: '100 − max(0, (1 − Reservesₜ / Reservesₜ₋₁) × 100)',
          numerator: 'Δ Policy Reserves',
          denominator: 'Prior-year Policy Reserves',
          numeratorValue: fmt(latest['reserves']),
          denominatorValue: fmt(prev['reserves']),
        );
      case 'benefit_ratio':
        return KpiFormula(
          expression: 'Policyholder Benefits / Premium × 100',
          numerator: 'Policyholder Benefits',
          denominator: 'Premium Earned',
          numeratorValue: fmt(latest['losses']),
          denominatorValue: fmt(latest['premiums']),
        );
    }
    return null;
  }

  static String? _methodologyFor(String code) {
    switch (code) {
      case 'loss_ratio':
      case 'expense_ratio':
      case 'combined_ratio':
      case 'underwriting_profit':
        return 'P&C industry-standard ratio. GAAP uses earned premium in the denominator; STAT uses written premium for the expense ratio. Source data: 10-K Income Statement plus footnote disclosure on incurred losses & LAE (ASC 944-40 / ASU 2015-09).';
      case 'investment_yield':
        return 'Approximate book yield computed as net investment income divided by mean policy/loss reserves. A truer yield would use mean invested-asset balance, which is not extracted from companyfacts.';
      case 'premium_growth':
        return 'Year-over-year change in earned premium. Reflects rate, exposure, and mix changes; for life, also reflects new sales and lapse activity.';
      case 'roe':
        return 'Return on Equity: net income / period-end stockholders\' equity. Trailing 12-month measure based on the latest filed 10-K.';
      case 'persistency':
        return 'Estimated retention proxy from policy-reserve continuity. Note: this is a derived approximation — actual persistency is disclosed by line of business in the 10-K Statistical Supplement, not in companyfacts XBRL.';
      case 'medical_loss_ratio':
        return 'Medical claims incurred as % of premium. ACA Section 2718 imposes minimum thresholds (85% large group / 80% individual) — failure triggers rebates.';
      case 'leverage':
        return 'Reserves-to-equity multiple. A coarse but widely watched solvency proxy. Watch alongside RBC ratio for full picture.';
      case 'benefit_ratio':
        return 'Life-insurer analog of the P&C loss ratio. Includes policyholder benefits (death claims, surrenders, withdrawals) over premium.';
    }
    return null;
  }

  static String? _slugFor(String code) {
    switch (code) {
      case 'loss_ratio':
      case 'expense_ratio':
      case 'combined_ratio':
      case 'underwriting_profit':
        return 'combined-ratio';
      case 'medical_loss_ratio':
        return 'combined-ratio';
      case 'investment_yield':
        return 'bonds-and-mbs';
      case 'persistency':
        return 'lfpb-future-policy-benefits';
      case 'benefit_ratio':
        return 'lfpb-future-policy-benefits';
      case 'leverage':
        return 'statutory-surplus-rbc';
      case 'roe':
        return 'asc-944-overview';
      case 'premium_growth':
        return 'premium-revenue-recognition';
    }
    return null;
  }

  // ---- P&C / Reinsurance / Multiline ---------------------------------------
  static KpiSet _pc(Map latest, Map prev, Map bench) {
    final prem = _num(latest['premiums']);
    final loss = _pct(_div(_num(latest['losses']), prem));
    final exp = _pct(_div(_num(latest['expenses']), prem));
    final combined = (loss != null && exp != null) ? loss + exp : null;
    final uwProfit = (prem != null && combined != null)
        ? prem * (1 - combined / 100.0)
        : null;

    final prevLoss = _pct(_div(_num(prev['losses']), _num(prev['premiums'])));
    final prevExp = _pct(_div(_num(prev['expenses']), _num(prev['premiums'])));
    final prevCombined =
        (prevLoss != null && prevExp != null) ? prevLoss + prevExp : null;

    final invYield =
        _pct(_div(_num(latest['investment_income']), _num(latest['reserves'])));
    final prevInvYield =
        _pct(_div(_num(prev['investment_income']), _num(prev['reserves'])));

    final premGrowth = _changePct(prem, _num(prev['premiums']));
    final roe =
        _pct(_div(_num(latest['net_income']), _num(latest['equity'])));
    final leverage = _round(_div(_num(latest['reserves']), _num(latest['equity'])));

    return KpiSet(
      insurerType: 'P&C',
      primary: [
        Kpi(
          code: 'loss_ratio',
          label: 'Loss Ratio',
          value: loss,
          unit: '%',
          direction: _dir(loss, prevLoss),
          deltaYoy: _change(loss, prevLoss),
          benchmark: _num(bench['loss_ratio']),
          status: _band(loss, 65, 75),
          description: 'Incurred losses & LAE as % of earned premium',
        ),
        Kpi(
          code: 'expense_ratio',
          label: 'Expense Ratio',
          value: exp,
          unit: '%',
          direction: _dir(exp, prevExp),
          deltaYoy: _change(exp, prevExp),
          benchmark: _num(bench['expense_ratio']),
          status: _band(exp, 26, 32),
          description: 'Underwriting expenses as % of earned premium',
        ),
        Kpi(
          code: 'combined_ratio',
          label: 'Combined Ratio',
          value: combined,
          unit: '%',
          direction: _dir(combined, prevCombined),
          deltaYoy: _change(combined, prevCombined),
          benchmark: _num(bench['combined_ratio']),
          status: _band(combined, 95, 100),
          description: 'Loss + Expense ratio. <100% = underwriting profit',
        ),
        Kpi(
          code: 'underwriting_profit',
          label: 'Underwriting Profit',
          value: uwProfit == null ? null : double.parse(uwProfit.toStringAsFixed(1)),
          unit: 'USD M',
          direction: (uwProfit ?? 0) > 0 ? 'up' : 'down',
          deltaYoy: null,
          benchmark: null,
          status: (uwProfit ?? 0) > 0 ? 'good' : 'warn',
          description: 'Earned premium × (1 − Combined Ratio)',
        ),
      ],
      secondary: [
        Kpi(
          code: 'investment_yield',
          label: 'Investment Yield',
          value: invYield,
          unit: '%',
          direction: _dir(invYield, prevInvYield),
          deltaYoy: _change(invYield, prevInvYield),
          benchmark: _num(bench['investment_yield']),
          status: _bandHigher(invYield, 4.0, 3.0),
          description: 'Net investment income / reserves',
        ),
        Kpi(
          code: 'premium_growth',
          label: 'Premium Growth',
          value: premGrowth,
          unit: '%',
          direction: _dir(prem, _num(prev['premiums'])),
          deltaYoy: null,
          benchmark: null,
          status: _bandHigher(premGrowth, 5.0, 0.0),
          description: 'Year-over-year change in earned premium',
        ),
        Kpi(
          code: 'leverage',
          label: 'Reserves / Equity',
          value: leverage,
          unit: 'x',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: (leverage ?? 0) > 5 ? 'warn' : 'good',
          description: 'Reserve leverage relative to equity',
        ),
        Kpi(
          code: 'roe',
          label: 'Return on Equity',
          value: roe,
          unit: '%',
          direction: _dir(_num(latest['net_income']), _num(prev['net_income'])),
          deltaYoy: null,
          benchmark: null,
          status: _bandHigher(roe, 12.0, 6.0),
          description: 'Net income / shareholders\' equity',
        ),
      ],
    );
  }

  // ---- Life ----------------------------------------------------------------
  static KpiSet _life(Map latest, Map prev, Map bench) {
    final prem = _num(latest['premiums']);
    final invYield =
        _pct(_div(_num(latest['investment_income']), _num(latest['reserves'])));
    final prevInvYield =
        _pct(_div(_num(prev['investment_income']), _num(prev['reserves'])));
    final persistency = _persistency(latest, prev);
    final growth = _changePct(prem, _num(prev['premiums']));
    final benefitRatio = _pct(_div(_num(latest['losses']), prem));

    return KpiSet(
      insurerType: 'Life',
      primary: [
        Kpi(
          code: 'persistency',
          label: 'Persistency Ratio',
          value: persistency,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: _num(bench['persistency']) ?? 92.0,
          status: _bandHigher(persistency, 90, 85),
          description: 'Estimated retention from premium and reserve continuity',
        ),
        Kpi(
          code: 'investment_yield',
          label: 'Investment Yield',
          value: invYield,
          unit: '%',
          direction: _dir(invYield, prevInvYield),
          deltaYoy: _change(invYield, prevInvYield),
          benchmark: _num(bench['investment_yield']) ?? 4.6,
          status: _bandHigher(invYield, 4.5, 3.5),
          description: 'Net investment income / policy reserves',
        ),
        Kpi(
          code: 'premium_growth',
          label: 'Premium Growth',
          value: growth,
          unit: '%',
          direction: _dir(prem, _num(prev['premiums'])),
          deltaYoy: null,
          benchmark: _num(bench['premium_growth']) ?? 4.0,
          status: _bandHigher(growth, 4.0, 0.0),
          description: 'Year-over-year change in gross premium',
        ),
        Kpi(
          code: 'benefit_ratio',
          label: 'Benefit Ratio',
          value: benefitRatio,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: _band(benefitRatio, 70, 80),
          description: 'Policyholder benefits / premium',
        ),
      ],
      secondary: [
        Kpi(
          code: 'expense_ratio',
          label: 'Expense Ratio',
          value: _pct(_div(_num(latest['expenses']), prem)),
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: 'neutral',
          description: 'Operating expenses / premium',
        ),
        Kpi(
          code: 'roe',
          label: 'Return on Equity',
          value: _pct(_div(_num(latest['net_income']), _num(latest['equity']))),
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: 'neutral',
          description: 'Net income / shareholders\' equity',
        ),
        Kpi(
          code: 'leverage',
          label: 'Reserves / Equity',
          value: _round(_div(_num(latest['reserves']), _num(latest['equity']))),
          unit: 'x',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: 'neutral',
          description: 'Reserve leverage',
        ),
      ],
    );
  }

  // ---- Health --------------------------------------------------------------
  static KpiSet _health(Map latest, Map prev, Map bench) {
    final prem = _num(latest['premiums']);
    final mlr = _pct(_div(_num(latest['losses']), prem));
    final expense = _pct(_div(_num(latest['expenses']), prem));
    final growth = _changePct(prem, _num(prev['premiums']));
    final roe = _pct(_div(_num(latest['net_income']), _num(latest['equity'])));
    return KpiSet(
      insurerType: 'Health',
      primary: [
        Kpi(
          code: 'medical_loss_ratio',
          label: 'Medical Loss Ratio',
          value: mlr,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: _num(bench['medical_loss_ratio']) ?? 86.5,
          status: _band(mlr, 85, 90),
          description: 'Medical claims / premium (ACA min 85%)',
        ),
        Kpi(
          code: 'expense_ratio',
          label: 'Expense Ratio',
          value: expense,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: _num(bench['expense_ratio']) ?? 11.0,
          status: _band(expense, 12, 16),
          description: 'Operating expense / premium',
        ),
        Kpi(
          code: 'premium_growth',
          label: 'Premium Growth',
          value: growth,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: _bandHigher(growth, 5.0, 0.0),
          description: 'Year-over-year change in premium revenue',
        ),
        Kpi(
          code: 'roe',
          label: 'Return on Equity',
          value: roe,
          unit: '%',
          direction: 'flat',
          deltaYoy: null,
          benchmark: null,
          status: _bandHigher(roe, 12, 6),
          description: 'Net income / equity',
        ),
      ],
      secondary: const [],
    );
  }

  // ---- Risk radar ----------------------------------------------------------
  static RiskRadar computeRisk({
    required String insurerType,
    required KpiSet kpis,
  }) {
    final by = {for (final k in [...kpis.primary, ...kpis.secondary]) k.code: k};

    RiskFactor factor(String label, String code, {bool invert = false}) {
      final k = by[code];
      if (k == null || k.value == null) {
        return RiskFactor(
            label: label, score: 50, band: 'yellow', note: 'Insufficient data');
      }
      double score;
      switch (k.status) {
        case 'good':
          score = 25;
          break;
        case 'warn':
          score = 55;
          break;
        case 'bad':
          score = 80;
          break;
        default:
          score = 50;
      }
      if (invert) score = 100 - score;
      final band = score < 40 ? 'green' : (score < 65 ? 'yellow' : 'red');
      return RiskFactor(
        label: label,
        score: score,
        band: band,
        note: '${k.label}: ${k.value!.toStringAsFixed(2)}${k.unit}',
      );
    }

    List<RiskFactor> factors;
    if (insurerType == 'Life') {
      factors = [
        factor('Persistency', 'persistency', invert: true),
        factor('Investment', 'investment_yield', invert: true),
        factor('Growth', 'premium_growth', invert: true),
        factor('Benefit pressure', 'benefit_ratio'),
        factor('Leverage', 'leverage'),
        factor('Profitability', 'roe', invert: true),
      ];
    } else if (insurerType == 'Health') {
      factors = [
        factor('MLR', 'medical_loss_ratio'),
        factor('Expense', 'expense_ratio'),
        factor('Growth', 'premium_growth', invert: true),
        factor('Profitability', 'roe', invert: true),
      ];
    } else {
      factors = [
        factor('Underwriting', 'combined_ratio'),
        factor('Loss exposure', 'loss_ratio'),
        factor('Expense discipline', 'expense_ratio'),
        factor('Investment', 'investment_yield', invert: true),
        factor('Growth', 'premium_growth', invert: true),
        factor('Leverage', 'leverage'),
      ];
    }
    final overall = factors.isEmpty
        ? 50.0
        : factors.fold<double>(0, (a, f) => a + f.score) / factors.length;
    return RiskRadar(
      overall: double.parse(overall.toStringAsFixed(2)),
      factors: factors,
    );
  }

  static double compositeScore(KpiSet kpis, RiskRadar risk) {
    final risk100 = (100 - risk.overall).clamp(0.0, 100.0);
    final goodCount = kpis.primary.where((k) => k.status == 'good').length;
    final total = kpis.primary.isEmpty ? 1 : kpis.primary.length;
    final kpiPct = goodCount / total * 100;
    return double.parse((0.6 * risk100 + 0.4 * kpiPct).toStringAsFixed(1));
  }

  // ---- helpers -------------------------------------------------------------
  static double? _num(dynamic v) {
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v);
    return null;
  }

  static double? _div(double? a, double? b) {
    if (a == null || b == null || b == 0) return null;
    return a / b;
  }

  static double? _pct(double? v) {
    if (v == null) return null;
    return double.parse((v * 100).toStringAsFixed(2));
  }

  static double? _round(double? v, [int dp = 2]) =>
      v == null ? null : double.parse(v.toStringAsFixed(dp));

  static double? _changePct(double? curr, double? prev) {
    if (curr == null || prev == null || prev == 0) return null;
    return double.parse(((curr - prev) / prev.abs() * 100).toStringAsFixed(2));
  }

  static double? _change(double? curr, double? prev) =>
      _changePct(curr, prev);

  static String _dir(double? curr, double? prev) {
    if (curr == null || prev == null) return 'flat';
    if (curr > prev * 1.005) return 'up';
    if (curr < prev * 0.995) return 'down';
    return 'flat';
  }

  static String _band(double? v, double goodMax, double warnMax) {
    if (v == null) return 'neutral';
    if (v <= goodMax) return 'good';
    if (v <= warnMax) return 'warn';
    return 'bad';
  }

  static String _bandHigher(double? v, double goodMin, double warnMin) {
    if (v == null) return 'neutral';
    if (v >= goodMin) return 'good';
    if (v >= warnMin) return 'warn';
    return 'bad';
  }

  static double? _persistency(Map latest, Map prev) {
    final pNow = _num(latest['premiums']) ?? 0;
    final pPrev = _num(prev['premiums']) ?? 0;
    final rNow = _num(latest['reserves']) ?? 0;
    final rPrev = _num(prev['reserves']) ?? 0;
    if (pPrev == 0 || rPrev == 0) return null;
    final pers = 100 - (1 - rNow / rPrev).clamp(0, 1) * 100;
    final growth = ((pNow - pPrev) / pPrev * 100).clamp(0, 5);
    return double.parse((pers + growth * 0.2).clamp(0, 99).toStringAsFixed(2));
  }
}
