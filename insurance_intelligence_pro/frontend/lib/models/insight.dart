class Insight {
  final String title;
  final String? detail;
  final String level;
  final String icon;
  final List<String> tags;

  const Insight({
    required this.title,
    this.detail,
    required this.level,
    required this.icon,
    required this.tags,
  });

  factory Insight.fromJson(Map<String, dynamic> j) => Insight(
        title: j['title'] ?? '',
        detail: j['detail'],
        level: j['level'] ?? 'neutral',
        icon: j['icon'] ?? 'spark',
        tags: ((j['tags'] as List?) ?? []).map((e) => e.toString()).toList(),
      );
}

class TrendIndicator {
  final String label;
  final String value;
  final double? delta;
  final String direction;
  TrendIndicator({
    required this.label,
    required this.value,
    this.delta,
    required this.direction,
  });

  factory TrendIndicator.fromJson(Map<String, dynamic> j) => TrendIndicator(
        label: j['label'] ?? '',
        value: j['value']?.toString() ?? '',
        delta: (j['delta'] as num?)?.toDouble(),
        direction: j['direction'] ?? 'flat',
      );
}

class MarketPulse {
  final String headline;
  final List<Insight> insights;
  final List<TrendIndicator> indicators;
  final List<List<double>> sparklines;
  MarketPulse({
    required this.headline,
    required this.insights,
    required this.indicators,
    required this.sparklines,
  });

  factory MarketPulse.fromJson(Map<String, dynamic> j) => MarketPulse(
        headline: j['headline'] ?? '',
        insights: ((j['insights'] as List?) ?? [])
            .map((e) => Insight.fromJson(e as Map<String, dynamic>))
            .toList(),
        indicators: ((j['indicators'] as List?) ?? [])
            .map((e) => TrendIndicator.fromJson(e as Map<String, dynamic>))
            .toList(),
        sparklines: ((j['sparklines'] as List?) ?? [])
            .map<List<double>>((row) =>
                (row as List).map((e) => (e as num).toDouble()).toList())
            .toList(),
      );
}

class NewsItem {
  final String title;
  final String url;
  final String source;
  final String? published;
  final String? summary;
  final String category;
  final String impact;
  NewsItem({
    required this.title,
    required this.url,
    required this.source,
    this.published,
    this.summary,
    required this.category,
    required this.impact,
  });

  factory NewsItem.fromJson(Map<String, dynamic> j) => NewsItem(
        title: j['title'] ?? '',
        url: j['url'] ?? '',
        source: j['source'] ?? '',
        published: j['published'],
        summary: j['summary'],
        category: j['category'] ?? 'General',
        impact: j['impact'] ?? 'Medium',
      );
}

class KnowledgeSection {
  final String heading;
  final String kind;
  final String? content;
  final List<String> items;

  KnowledgeSection({
    required this.heading,
    required this.kind,
    this.content,
    required this.items,
  });

  factory KnowledgeSection.fromJson(Map<String, dynamic> j) => KnowledgeSection(
        heading: j['heading']?.toString() ?? '',
        kind: j['kind']?.toString() ?? 'text',
        content: j['content']?.toString(),
        items: ((j['items'] as List?) ?? [])
            .map((e) => e.toString())
            .toList(),
      );
}

class FsliRow {
  final String lineItem;
  final String gaap;
  final String stat;
  final String delta;

  FsliRow({
    required this.lineItem,
    required this.gaap,
    required this.stat,
    required this.delta,
  });

  factory FsliRow.fromJson(Map<String, dynamic> j) => FsliRow(
        lineItem: j['line_item']?.toString() ?? '',
        gaap: j['gaap']?.toString() ?? '',
        stat: j['stat']?.toString() ?? '',
        delta: j['delta']?.toString() ?? '',
      );
}

class KnowledgeReference {
  final String label;
  final String url;
  KnowledgeReference({required this.label, required this.url});

  factory KnowledgeReference.fromJson(Map<String, dynamic> j) =>
      KnowledgeReference(
        label: j['label']?.toString() ?? '',
        url: j['url']?.toString() ?? '',
      );
}

class KnowledgeArticle {
  final String slug;
  final String title;
  final String framework;
  final String depth;
  final String summary;
  final String bodyMd;
  final String? fsli;
  final String? gaapView;
  final String? statView;
  final String? keyDifference;
  final String? lastUpdated;
  final List<KnowledgeSection> sections;
  final List<FsliRow> fsliTable;
  final List<KnowledgeReference> references;
  final List<String> tags;

  KnowledgeArticle({
    required this.slug,
    required this.title,
    required this.framework,
    required this.depth,
    required this.summary,
    required this.bodyMd,
    this.fsli,
    this.gaapView,
    this.statView,
    this.keyDifference,
    this.lastUpdated,
    required this.sections,
    required this.fsliTable,
    required this.references,
    required this.tags,
  });

  factory KnowledgeArticle.fromJson(Map<String, dynamic> j) => KnowledgeArticle(
        slug: j['slug']?.toString() ?? '',
        title: j['title']?.toString() ?? '',
        framework: j['framework']?.toString() ?? '',
        depth: j['depth']?.toString() ?? 'overview',
        summary: j['summary']?.toString() ?? '',
        bodyMd: j['body_md']?.toString() ?? '',
        fsli: j['fsli']?.toString(),
        gaapView: j['gaap_view']?.toString(),
        statView: j['stat_view']?.toString(),
        keyDifference: j['key_difference']?.toString(),
        lastUpdated: j['last_updated']?.toString(),
        sections: ((j['sections'] as List?) ?? [])
            .map((e) =>
                KnowledgeSection.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        fsliTable: ((j['fsli_table'] as List?) ?? [])
            .map((e) =>
                FsliRow.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        references: ((j['references'] as List?) ?? [])
            .map((e) => KnowledgeReference.fromJson(
                Map<String, dynamic>.from(e as Map)))
            .toList(),
        tags: ((j['tags'] as List?) ?? []).map((e) => e.toString()).toList(),
      );

  /// Concatenated search corpus — every field flattened so the search
  /// bar finds matches in section headings, FSLI rows, and references.
  String get searchCorpus {
    final parts = <String>[
      title,
      slug,
      framework,
      summary,
      bodyMd,
      fsli ?? '',
      gaapView ?? '',
      statView ?? '',
      keyDifference ?? '',
      tags.join(' '),
      for (final s in sections) ...[
        s.heading,
        s.content ?? '',
        s.items.join(' '),
      ],
      for (final r in fsliTable) '${r.lineItem} ${r.gaap} ${r.stat} ${r.delta}',
      for (final ref in references) ref.label,
    ];
    return parts.join(' \n ').toLowerCase();
  }
}
