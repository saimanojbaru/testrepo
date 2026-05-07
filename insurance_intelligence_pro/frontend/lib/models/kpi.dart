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

  Kpi({
    required this.code,
    required this.label,
    required this.value,
    required this.unit,
    required this.direction,
    required this.deltaYoy,
    required this.benchmark,
    required this.status,
    required this.description,
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
      );
}

class KpiSet {
  final String insurerType;
  final List<Kpi> primary;
  final List<Kpi> secondary;

  KpiSet({required this.insurerType, required this.primary, required this.secondary});

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
  RiskFactor({required this.label, required this.score, required this.band, this.note});

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
  RiskRadar({required this.overall, required this.factors});

  factory RiskRadar.fromJson(Map<String, dynamic> j) => RiskRadar(
        overall: (j['overall'] as num? ?? 50).toDouble(),
        factors: ((j['factors'] as List?) ?? [])
            .map((e) => RiskFactor.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
