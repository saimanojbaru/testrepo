class FilingSource {
  final String form;
  final String fiscalYear;
  final String filedDate;
  final String url;

  const FilingSource({
    required this.form,
    required this.fiscalYear,
    required this.filedDate,
    required this.url,
  });

  factory FilingSource.fromJson(Map<String, dynamic> j) => FilingSource(
        form: j['form']?.toString() ?? '10-K',
        fiscalYear: j['fiscal_year']?.toString() ?? '',
        filedDate: j['filed_date']?.toString() ?? '',
        url: j['url']?.toString() ?? '',
      );

  String get displayLabel =>
      '$form · $fiscalYear${filedDate.isEmpty ? "" : " · filed $filedDate"}';
}

class KpiFormula {
  final String expression;
  final String numerator;
  final String denominator;
  final String numeratorValue;
  final String denominatorValue;

  const KpiFormula({
    required this.expression,
    required this.numerator,
    required this.denominator,
    required this.numeratorValue,
    required this.denominatorValue,
  });
}

class Kpi {
  final String code;
  final String label;
  final double? value;
  final String unit;
  final String direction;
  final double? deltaYoy;
  final double? benchmark;
  final String status;
  final String? description;
  final FilingSource? source;
  final KpiFormula? formula;
  final String? methodology;
  final String? knowledgeSlug;

  const Kpi({
    required this.code,
    required this.label,
    required this.value,
    required this.unit,
    required this.direction,
    required this.deltaYoy,
    required this.benchmark,
    required this.status,
    required this.description,
    this.source,
    this.formula,
    this.methodology,
    this.knowledgeSlug,
  });

  factory Kpi.fromJson(Map<String, dynamic> j) => Kpi(
        code: j['code'] ?? '',
        label: j['label'] ?? '',
        value: (j['value'] as num?)?.toDouble(),
        unit: j['unit'] ?? '%',
        direction: j['direction'] ?? 'neutral',
        deltaYoy: (j['delta_yoy'] as num?)?.toDouble(),
        benchmark: (j['benchmark'] as num?)?.toDouble(),
        status: j['status'] ?? 'neutral',
        description: j['description'],
        source: j['source'] is Map
            ? FilingSource.fromJson(Map<String, dynamic>.from(j['source']))
            : null,
      );
}

class KpiSet {
  final String insurerType;
  final List<Kpi> primary;
  final List<Kpi> secondary;

  const KpiSet(
      {required this.insurerType, required this.primary, required this.secondary});

  factory KpiSet.fromJson(Map<String, dynamic> j) => KpiSet(
        insurerType: j['insurer_type'] ?? 'Unknown',
        primary: ((j['primary'] as List?) ?? [])
            .map((e) => Kpi.fromJson(e as Map<String, dynamic>))
            .toList(),
        secondary: ((j['secondary'] as List?) ?? [])
            .map((e) => Kpi.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class RiskFactor {
  final String label;
  final double score;
  final String band;
  final String? note;
  const RiskFactor(
      {required this.label, required this.score, required this.band, this.note});

  factory RiskFactor.fromJson(Map<String, dynamic> j) => RiskFactor(
        label: j['label'] ?? '',
        score: (j['score'] as num? ?? 0).toDouble(),
        band: j['band'] ?? 'yellow',
        note: j['note'],
      );
}

class RiskRadar {
  final double overall;
  final List<RiskFactor> factors;
  const RiskRadar({required this.overall, required this.factors});

  factory RiskRadar.fromJson(Map<String, dynamic> j) => RiskRadar(
        overall: (j['overall'] as num? ?? 50).toDouble(),
        factors: ((j['factors'] as List?) ?? [])
            .map((e) => RiskFactor.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
