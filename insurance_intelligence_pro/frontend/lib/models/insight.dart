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

class KnowledgeArticle {
  final String slug;
  final String title;
  final String framework;
  final String summary;
  final String bodyMd;
  final String? fsli;
  final String? gaapView;
  final String? statView;
  final List<String> tags;

  KnowledgeArticle({
    required this.slug,
    required this.title,
    required this.framework,
    required this.summary,
    required this.bodyMd,
    this.fsli,
    this.gaapView,
    this.statView,
    required this.tags,
  });

  factory KnowledgeArticle.fromJson(Map<String, dynamic> j) => KnowledgeArticle(
        slug: j['slug'] ?? '',
        title: j['title'] ?? '',
        framework: j['framework'] ?? '',
        summary: j['summary'] ?? '',
        bodyMd: j['body_md'] ?? '',
        fsli: j['fsli'],
        gaapView: j['gaap_view'],
        statView: j['stat_view'],
        tags: ((j['tags'] as List?) ?? []).map((e) => e.toString()).toList(),
      );
}
