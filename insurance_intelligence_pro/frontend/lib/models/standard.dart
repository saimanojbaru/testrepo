/// Models for the four-section Accounting / Auditing Standard schema:
/// chapter_content / need / evolution / gaap_comparison.
class StandardEvolution {
  final String year;
  final String change;
  const StandardEvolution({required this.year, required this.change});
  factory StandardEvolution.fromJson(Map<String, dynamic> j) =>
      StandardEvolution(
        year: j['year']?.toString() ?? '',
        change: j['change']?.toString() ?? '',
      );
}

class GaapComparisonRow {
  final String aspect;
  final String gaap;
  final String stat;
  final String delta;
  const GaapComparisonRow({
    required this.aspect,
    required this.gaap,
    required this.stat,
    required this.delta,
  });
  factory GaapComparisonRow.fromJson(Map<String, dynamic> j) =>
      GaapComparisonRow(
        aspect: j['aspect']?.toString() ?? '',
        gaap: j['gaap']?.toString() ?? '',
        stat: j['stat']?.toString() ?? '',
        delta: j['delta']?.toString() ?? '',
      );
}

class Standard {
  final String id;
  final String framework; // SSAP / ASC944 / PCAOB
  final String number;
  final String title;
  final String? fsli;
  final String summary;
  final String chapterContent;
  final String need;
  final List<StandardEvolution> evolution;
  final List<GaapComparisonRow> gaapComparison;
  final List<String> tags;

  Standard({
    required this.id,
    required this.framework,
    required this.number,
    required this.title,
    this.fsli,
    required this.summary,
    required this.chapterContent,
    required this.need,
    required this.evolution,
    required this.gaapComparison,
    required this.tags,
  });

  factory Standard.fromJson(Map<String, dynamic> j, {String? frameworkOverride}) {
    return Standard(
      id: j['id']?.toString() ?? '',
      framework: frameworkOverride ?? j['framework']?.toString() ?? '',
      number: j['number']?.toString() ?? '',
      title: j['title']?.toString() ?? '',
      fsli: j['fsli']?.toString(),
      summary: j['summary']?.toString() ?? '',
      chapterContent: j['chapter_content']?.toString() ?? '',
      need: j['need']?.toString() ?? '',
      evolution: ((j['evolution'] as List?) ?? [])
          .map((e) =>
              StandardEvolution.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      gaapComparison: ((j['gaap_comparison'] as List?) ?? [])
          .map((e) =>
              GaapComparisonRow.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      tags: ((j['tags'] as List?) ?? []).map((e) => e.toString()).toList(),
    );
  }

  String get displayLabel => '$number — $title';

  String get searchCorpus => [
        id,
        number,
        title,
        fsli ?? '',
        summary,
        chapterContent,
        need,
        ...tags,
        for (final e in evolution) '${e.year} ${e.change}',
        for (final r in gaapComparison)
          '${r.aspect} ${r.gaap} ${r.stat} ${r.delta}',
      ].join(' ').toLowerCase();
}

class StandardsCatalog {
  final String framework;
  final String manual;
  final String publisher;
  final String sourceUrl;
  final List<Standard> standards;

  const StandardsCatalog({
    required this.framework,
    required this.manual,
    required this.publisher,
    required this.sourceUrl,
    required this.standards,
  });

  factory StandardsCatalog.fromJson(Map<String, dynamic> j) {
    final framework = j['framework']?.toString() ?? '';
    return StandardsCatalog(
      framework: framework,
      manual: j['manual']?.toString() ?? '',
      publisher: j['publisher']?.toString() ?? '',
      sourceUrl: j['source_url']?.toString() ?? '',
      standards: ((j['standards'] as List?) ?? [])
          .map((e) => Standard.fromJson(
              Map<String, dynamic>.from(e as Map),
              frameworkOverride: framework))
          .toList(),
    );
  }
}
