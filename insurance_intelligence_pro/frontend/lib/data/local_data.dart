import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

/// Loads bundled JSON datasets once and caches them in memory.
///
/// The app ships entirely offline — every datum (tickers, sample
/// financials, knowledge articles, dashboard pulse) lives in
/// `assets/data/*.json` and is parsed at startup.
class LocalData {
  LocalData._();
  static final LocalData instance = LocalData._();

  bool _loaded = false;
  late Map<String, dynamic> _tickers;
  late Map<String, dynamic> _sample;
  late Map<String, dynamic> _knowledge;

  Future<void> load() async {
    if (_loaded) return;
    final tickers = await rootBundle.loadString('assets/data/tickers.json');
    final sample = await rootBundle.loadString('assets/data/sample_data.json');
    final knowledge =
        await rootBundle.loadString('assets/data/knowledge.json');
    _tickers = jsonDecode(tickers) as Map<String, dynamic>;
    _sample = jsonDecode(sample) as Map<String, dynamic>;
    _knowledge = jsonDecode(knowledge) as Map<String, dynamic>;
    _loaded = true;
  }

  List<Map<String, dynamic>> get companies =>
      ((_tickers['companies'] as List?) ?? [])
          .cast<Map<String, dynamic>>();

  Map<String, List<String>> get peerGroups {
    final raw = (_tickers['peer_groups'] as Map?) ?? {};
    return raw.map((k, v) =>
        MapEntry(k.toString(), (v as List).map((e) => e.toString()).toList()));
  }

  Map<String, dynamic> get sampleMetrics =>
      (_sample['metrics'] as Map?)?.cast<String, dynamic>() ?? {};

  Map<String, dynamic> get industryBenchmarks =>
      (_sample['industry_benchmarks'] as Map?)?.cast<String, dynamic>() ?? {};

  List<Map<String, dynamic>> get knowledgeArticles =>
      ((_knowledge['articles'] as List?) ?? [])
          .cast<Map<String, dynamic>>();

  /// Resolve a free-form query (ticker or partial name) to a company record.
  Map<String, dynamic>? resolveCompany(String query) {
    final q = query.trim().toUpperCase();
    if (q.isEmpty) return null;
    for (final c in companies) {
      if (c['ticker'].toString().toUpperCase() == q) return c;
    }
    final qDot = q.replaceAll('-', '.');
    for (final c in companies) {
      if (c['ticker'].toString().toUpperCase() == qDot) return c;
    }
    final byName = companies.where((c) =>
        c['name'].toString().toUpperCase().contains(q));
    if (byName.isEmpty) return null;
    final list = byName.toList()
      ..sort((a, b) =>
          (a['name'].toString().length).compareTo(b['name'].toString().length));
    return list.first;
  }

  /// Pull the pre-curated fiscal history for a ticker, with sensible
  /// fallbacks (DEFAULT_PC / DEFAULT_LIFE) so any input renders.
  Map<String, dynamic> fiscalDataFor(String ticker, String insurerType) {
    final upper = ticker.toUpperCase();
    if (sampleMetrics.containsKey(upper)) {
      return sampleMetrics[upper] as Map<String, dynamic>;
    }
    final fallbackKey = insurerType == 'Life' ? 'DEFAULT_LIFE' : 'DEFAULT_PC';
    return sampleMetrics[fallbackKey] as Map<String, dynamic>;
  }

  List<String> peerTickersFor(String insurerType) =>
      peerGroups[insurerType] ?? const [];
}
