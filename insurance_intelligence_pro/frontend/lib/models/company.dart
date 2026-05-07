import 'kpi.dart';
import 'insight.dart';

class Company {
  final String cik;
  final String ticker;
  final String name;
  final String? sic;
  final String insurerType;
  final String? exchange;

  Company({
    required this.cik,
    required this.ticker,
    required this.name,
    this.sic,
    required this.insurerType,
    this.exchange,
  });

  factory Company.fromJson(Map<String, dynamic> j) => Company(
        cik: (j['cik'] ?? '').toString(),
        ticker: (j['ticker'] ?? '').toString(),
        name: (j['name'] ?? '').toString(),
        sic: j['sic']?.toString(),
        insurerType: (j['insurer_type'] ?? 'Unknown').toString(),
        exchange: j['exchange']?.toString(),
      );
}

class FinancialPoint {
  final double fy;
  final double value;
  FinancialPoint(this.fy, this.value);
}

class FinancialSeries {
  final String label;
  final String unit;
  final List<FinancialPoint> points;
  FinancialSeries({required this.label, required this.unit, required this.points});

  factory FinancialSeries.fromJson(Map<String, dynamic> j) => FinancialSeries(
        label: j['label'] ?? '',
        unit: j['unit'] ?? '',
        points: ((j['points'] as List?) ?? [])
            .map((e) => FinancialPoint(
                  (e['fy'] as num).toDouble(),
                  (e['value'] as num?)?.toDouble() ?? 0,
                ))
            .toList(),
      );
}

class CompanyAnalysis {
  final Company company;
  final double score;
  final String headline;
  final int? lastFiscalYear;
  final KpiSet kpis;
  final RiskRadar riskRadar;
  final List<Insight> insights;
  final List<FinancialSeries> series;
  final Map<String, dynamic> rawMetrics;
  final String dataSource;
  final bool isEstimated;

  CompanyAnalysis({
    required this.company,
    required this.score,
    required this.headline,
    this.lastFiscalYear,
    required this.kpis,
    required this.riskRadar,
    required this.insights,
    required this.series,
    required this.rawMetrics,
    required this.dataSource,
    required this.isEstimated,
  });

  factory CompanyAnalysis.fromJson(Map<String, dynamic> j) => CompanyAnalysis(
        company: Company.fromJson((j['company'] ?? {}) as Map<String, dynamic>),
        score: (j['score'] as num? ?? 0).toDouble(),
        headline: j['headline'] ?? '',
        lastFiscalYear: (j['last_fiscal_year'] as num?)?.toInt(),
        kpis: KpiSet.fromJson((j['kpis'] ?? {}) as Map<String, dynamic>),
        riskRadar:
            RiskRadar.fromJson((j['risk_radar'] ?? {}) as Map<String, dynamic>),
        insights: ((j['insights'] as List?) ?? [])
            .map((e) => Insight.fromJson(e as Map<String, dynamic>))
            .toList(),
        series: ((j['series'] as List?) ?? [])
            .map((e) => FinancialSeries.fromJson(e as Map<String, dynamic>))
            .toList(),
        rawMetrics: Map<String, dynamic>.from(j['raw_metrics'] ?? {}),
        dataSource: j['data_source'] ?? 'SEC EDGAR',
        isEstimated: j['is_estimated'] == true,
      );
}
