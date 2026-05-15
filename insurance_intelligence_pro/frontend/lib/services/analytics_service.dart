import '../data/local_data.dart';
import '../engines/insight_engine.dart';
import '../engines/kpi_engine.dart';
import '../models/company.dart';
import '../models/insight.dart';
import '../models/kpi.dart';

class _ScoredArticle {
  final Map<String, dynamic> article;
  final int score;
  _ScoredArticle(this.article, this.score);
}

/// Offline-first analytics facade.
///
/// All five tabs talk to this. There is no network call, no backend
/// dependency: the curated dataset bundled in `assets/data/` is the
/// single source of truth, and the Dart engines compute on top of it.
class AnalyticsService {
  AnalyticsService._();
  static final AnalyticsService instance = AnalyticsService._();

  LocalData get _data => LocalData.instance;

  // ---- Search / suggestions ------------------------------------------------

  Map<String, dynamic>? resolveCompany(String query) =>
      _data.resolveCompany(query);

  List<Map<String, dynamic>> suggest(String query, {int limit = 8}) {
    final q = query.trim().toUpperCase();
    if (q.isEmpty) return _data.companies.take(limit).toList();
    final ranked = <_Suggestion>[];
    for (final c in _data.companies) {
      final t = c['ticker'].toString().toUpperCase();
      final n = c['name'].toString().toUpperCase();
      int score = -1;
      if (t == q) {
        score = 100;
      } else if (t.startsWith(q)) {
        score = 90;
      } else if (n.startsWith(q)) {
        score = 70;
      } else if (n.contains(q) || t.contains(q)) {
        score = 40;
      }
      if (score >= 0) ranked.add(_Suggestion(c, score));
    }
    ranked.sort((a, b) {
      final s = b.score.compareTo(a.score);
      if (s != 0) return s;
      return (a.entry['name'].toString().length)
          .compareTo(b.entry['name'].toString().length);
    });
    return ranked.take(limit).map((e) => e.entry).toList();
  }

  // ---- Company analysis ----------------------------------------------------

  CompanyAnalysis analyzeCompany(String query) {
    final resolved = _data.resolveCompany(query);
    if (resolved == null) {
      return _emptyAnalysis(query);
    }
    final insurerType = (resolved['type'] ?? 'P&C').toString();
    final fiscal = _data.fiscalDataFor(resolved['ticker'].toString(), insurerType);
    final history = ((fiscal['fiscal_history'] as List?) ?? [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    final fy = (fiscal['fiscal_year'] as num?)?.toInt() ??
        (history.isNotEmpty ? (history.last['fy'] as num).toInt() : null);

    final kpis = KpiEngine.compute(
      insurerType: insurerType,
      history: history,
      benchmarks: _data.industryBenchmarks,
      ticker: resolved['ticker']?.toString(),
    );
    final radar = KpiEngine.computeRisk(insurerType: insurerType, kpis: kpis);
    final score = KpiEngine.compositeScore(kpis, radar);
    final insights = InsightEngine.forCompany(
      kpis: kpis,
      history: history,
      insurerType: insurerType,
    );

    return CompanyAnalysis(
      company: Company(
        cik: (resolved['cik'] ?? '').toString(),
        ticker: resolved['ticker'].toString(),
        name: resolved['name'].toString(),
        sic: resolved['sic']?.toString(),
        insurerType: insurerType,
        exchange: null,
      ),
      score: score,
      headline: _headline(insurerType, kpis, score),
      lastFiscalYear: fy,
      kpis: kpis,
      riskRadar: radar,
      insights: insights,
      series: _series(history),
      rawMetrics: history.isEmpty
          ? <String, dynamic>{}
          : Map<String, dynamic>.from(history.last)
        ..remove('fy'),
      dataSource: 'Curated dataset · v${DateTime.now().year}',
      isEstimated: !_data.sampleMetrics
          .containsKey(resolved['ticker'].toString().toUpperCase()),
    );
  }

  // ---- Compare -------------------------------------------------------------

  Map<String, dynamic> compare(List<String> tickers) {
    final resolved = tickers
        .map(_data.resolveCompany)
        .whereType<Map<String, dynamic>>()
        .toList();
    if (resolved.isEmpty) {
      return {
        'companies': <Map<String, dynamic>>[],
        'metrics': <Map<String, dynamic>>[],
        'verdict': 'Could not resolve any of the inputs.',
        'insurer_type': 'Unknown',
      };
    }
    final payloads = <Map<String, dynamic>>[];
    for (final c in resolved) {
      final type = (c['type'] ?? 'P&C').toString();
      final fiscal =
          _data.fiscalDataFor(c['ticker'].toString(), type);
      final history = ((fiscal['fiscal_history'] as List?) ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      final kpis = KpiEngine.compute(
        insurerType: type,
        history: history,
        benchmarks: _data.industryBenchmarks,
        ticker: c['ticker']?.toString(),
      );
      final radar = KpiEngine.computeRisk(insurerType: type, kpis: kpis);
      payloads.add({
        'company': c,
        'insurer_type': type,
        'fiscal_year': fiscal['fiscal_year'],
        'score': KpiEngine.compositeScore(kpis, radar),
        'kpis': _kpisToJson(kpis),
        'risk': _radarToJson(radar),
      });
    }
    final type = payloads.first['insurer_type'] as String;
    final metricSpecs = _metricSpecs(type);
    final metrics = metricSpecs.map((spec) {
      final values = payloads.map((p) {
        final code = spec.code;
        final byCode = (p['kpis']['primary'] as List)
            .followedBy(p['kpis']['secondary'] as List)
            .cast<Map<String, dynamic>>()
            .firstWhere((k) => k['code'] == code,
                orElse: () => <String, dynamic>{});
        return {
          'ticker': (p['company'] as Map)['ticker'],
          'value': byCode['value'],
        };
      }).toList();
      final ranked = _rank(values, lowerIsBetter: spec.lowerIsBetter);
      return {
        'code': spec.code,
        'label': spec.label,
        'lower_is_better': spec.lowerIsBetter,
        'values': ranked,
      };
    }).toList();

    return {
      'insurer_type': type,
      'companies': payloads,
      'metrics': metrics,
      'verdict': _verdict(payloads),
    };
  }

  Map<String, dynamic> autoCompare(String query) {
    final primary = _data.resolveCompany(query);
    if (primary == null) return compare([query]);
    final type = (primary['type'] ?? 'P&C').toString();
    final peers = [primary['ticker'].toString()];
    for (final t in _data.peerTickersFor(type)) {
      if (!peers.contains(t)) peers.add(t);
      if (peers.length >= 6) break;
    }
    return compare(peers);
  }

  // ---- Knowledge -----------------------------------------------------------

  List<Map<String, dynamic>> knowledge({String? framework}) {
    final all = _data.knowledgeArticles;
    if (framework == null || framework == 'All') return all;
    return all.where((a) => a['framework'] == framework).toList();
  }

  List<String> knowledgeFrameworks() {
    final set = <String>{};
    for (final a in _data.knowledgeArticles) {
      set.add(a['framework'].toString());
    }
    final list = set.toList()..sort();
    return ['All', ...list];
  }

  /// Deep search across every field in every article — including
  /// sections, FSLI table rows, and references.  Tokens (whitespace-
  /// separated) are AND-combined so "cash gaap" matches an article
  /// that mentions both terms anywhere.
  List<Map<String, dynamic>> knowledgeSearch(String query) {
    final tokens = query
        .trim()
        .toLowerCase()
        .split(RegExp(r'\s+'))
        .where((t) => t.isNotEmpty)
        .toList();
    if (tokens.isEmpty) return _data.knowledgeArticles;
    final scored = <_ScoredArticle>[];
    for (final raw in _data.knowledgeArticles) {
      final article = KnowledgeArticle.fromJson(Map<String, dynamic>.from(raw));
      final corpus = article.searchCorpus;
      final allMatch = tokens.every((t) => corpus.contains(t));
      if (!allMatch) continue;
      // Lightly rank: title hits and FSLI hits beat body-only matches.
      final titleHits = tokens
          .where((t) => article.title.toLowerCase().contains(t))
          .length;
      final fsliHits = tokens
          .where((t) =>
              (article.fsli ?? '').toLowerCase().contains(t) ||
              article.fsliTable.any((r) =>
                  r.lineItem.toLowerCase().contains(t)))
          .length;
      final score = titleHits * 4 + fsliHits * 2 + tokens.length;
      scored.add(_ScoredArticle(raw, score));
    }
    scored.sort((a, b) => b.score.compareTo(a.score));
    return scored.map((s) => s.article).toList();
  }

  // ---- Updates / news ------------------------------------------------------

  Map<String, dynamic> news({String? category}) {
    var items = InsightEngine.headlines;
    if (category != null && category != 'All') {
      items = items.where((n) => n['category'] == category).toList();
    }
    final categories = <String>{
      ...InsightEngine.headlines.map((n) => n['category'].toString())
    }.toList()
      ..sort();
    return {
      'items': items,
      'categories': ['All', ...categories],
    };
  }

  // ---- Dashboard -----------------------------------------------------------

  Map<String, dynamic> pulse() => {
        'headline': InsightEngine.pulseHeadline,
        'insights':
            InsightEngine.industryPulse.map(_insightToJson).toList(),
        'indicators': InsightEngine.industryIndicators,
        'sparklines':
            InsightEngine.sparklines.map((s) => s.toList()).toList(),
      };

  List<Map<String, dynamic>> topNews({int limit = 6}) =>
      InsightEngine.headlines.take(limit).toList();

  // ---- helpers -------------------------------------------------------------

  CompanyAnalysis _emptyAnalysis(String query) {
    return CompanyAnalysis(
      company: Company(
        cik: '',
        ticker: query.toUpperCase(),
        name: query,
        sic: null,
        insurerType: 'Unknown',
        exchange: null,
      ),
      score: 0,
      headline: 'We don\'t have curated data for "$query" yet — try a US-listed insurer ticker like PGR, MET, or CB.',
      lastFiscalYear: null,
      kpis: const KpiSet(insurerType: 'Unknown', primary: [], secondary: []),
      riskRadar: const RiskRadar(overall: 50, factors: []),
      insights: const [
        Insight(
          title: 'Search a curated insurer',
          detail:
              'Try one of: PGR, TRV, ALL, CB, AIG, MET, PRU, AFL, UNH, HUM, EG.',
          level: 'neutral',
          icon: 'info',
          tags: ['help'],
        )
      ],
      series: const [],
      rawMetrics: const {},
      dataSource: 'Curated dataset',
      isEstimated: true,
    );
  }

  String _headline(String type, KpiSet kpis, double score) {
    final by = {for (final k in kpis.primary) k.code: k};
    if (type == 'P&C' || type == 'Reinsurance' || type == 'Multiline') {
      final cr = by['combined_ratio'];
      if (cr?.value != null) {
        final mood = cr!.value! < 100 ? 'underwriting profit' : 'underwriting loss';
        return 'Combined ratio ${cr.value!.toStringAsFixed(1)}% — running at an $mood; health score ${score.toStringAsFixed(0)}/100.';
      }
    }
    if (type == 'Life') {
      final py = by['persistency'];
      final iy = by['investment_yield'];
      if (py?.value != null && iy?.value != null) {
        return 'Persistency ${py!.value!.toStringAsFixed(1)}% and yield ${iy!.value!.toStringAsFixed(2)}% drive a ${score.toStringAsFixed(0)}/100 score.';
      }
    }
    if (type == 'Health') {
      final mlr = by['medical_loss_ratio'];
      if (mlr?.value != null) {
        return 'Medical loss ratio ${mlr!.value!.toStringAsFixed(1)}% — score ${score.toStringAsFixed(0)}/100.';
      }
    }
    return 'Composite score ${score.toStringAsFixed(0)}/100.';
  }

  List<FinancialSeries> _series(List<Map<String, dynamic>> history) {
    if (history.isEmpty) return const [];
    const metrics = [
      ['premiums', 'Premium', 'USD M'],
      ['losses', 'Losses', 'USD M'],
      ['expenses', 'Expenses', 'USD M'],
      ['investment_income', 'Investment Income', 'USD M'],
      ['net_income', 'Net Income', 'USD M'],
    ];
    return [
      for (final m in metrics)
        FinancialSeries(
          label: m[1],
          unit: m[2],
          points: [
            for (final r in history)
              FinancialPoint(
                (r['fy'] as num).toDouble(),
                (r[m[0]] as num?)?.toDouble() ?? 0,
              ),
          ],
        ),
    ];
  }

  Map<String, dynamic> _kpisToJson(KpiSet set) => {
        'insurer_type': set.insurerType,
        'primary': set.primary.map(_kpiToJson).toList(),
        'secondary': set.secondary.map(_kpiToJson).toList(),
      };

  Map<String, dynamic> _kpiToJson(Kpi k) => {
        'code': k.code,
        'label': k.label,
        'value': k.value,
        'unit': k.unit,
        'direction': k.direction,
        'delta_yoy': k.deltaYoy,
        'benchmark': k.benchmark,
        'status': k.status,
        'description': k.description,
      };

  Map<String, dynamic> _radarToJson(RiskRadar r) => {
        'overall': r.overall,
        'factors': r.factors
            .map((f) =>
                {'label': f.label, 'score': f.score, 'band': f.band, 'note': f.note})
            .toList(),
      };

  Map<String, dynamic> _insightToJson(Insight i) => {
        'title': i.title,
        'detail': i.detail,
        'level': i.level,
        'icon': i.icon,
        'tags': i.tags,
      };

  List<Map<String, dynamic>> _rank(
      List<Map<String, dynamic>> values,
      {required bool lowerIsBetter}) {
    final sortable = values
        .where((v) => v['value'] != null)
        .toList()
      ..sort((a, b) {
        final av = (a['value'] as num).toDouble();
        final bv = (b['value'] as num).toDouble();
        return lowerIsBetter ? av.compareTo(bv) : bv.compareTo(av);
      });
    final rank = <String, int>{};
    for (var i = 0; i < sortable.length; i++) {
      rank[sortable[i]['ticker'].toString()] = i + 1;
    }
    return values
        .map((v) => {
              'ticker': v['ticker'],
              'value': v['value'],
              'rank': rank[v['ticker'].toString()],
            })
        .toList();
  }

  String _verdict(List<Map<String, dynamic>> payloads) {
    if (payloads.isEmpty) return 'No companies to compare.';
    final sorted = [...payloads]
      ..sort((a, b) =>
          (b['score'] as num).toDouble().compareTo((a['score'] as num).toDouble()));
    final top = sorted.first;
    final bottom = sorted.last;
    final topScore = (top['score'] as num).toDouble();
    final bottomScore = (bottom['score'] as num).toDouble();
    if ((topScore - bottomScore) < 5) {
      return 'Tightly bunched — ${(top['company'] as Map)['name']} narrowly leads on a balanced KPI mix.';
    }
    return '${(top['company'] as Map)['name']} (${(top['company'] as Map)['ticker']}) leads with a '
        '${topScore.toStringAsFixed(0)}/100 score; ${(bottom['company'] as Map)['name']} '
        'trails at ${bottomScore.toStringAsFixed(0)}/100, mainly on underwriting and capital metrics.';
  }

  List<_MetricSpec> _metricSpecs(String type) {
    if (type == 'Life') {
      return const [
        _MetricSpec('persistency', 'Persistency', false),
        _MetricSpec('investment_yield', 'Investment Yield', false),
        _MetricSpec('premium_growth', 'Premium Growth', false),
        _MetricSpec('benefit_ratio', 'Benefit Ratio', true),
        _MetricSpec('roe', 'ROE', false),
      ];
    }
    if (type == 'Health') {
      return const [
        _MetricSpec('medical_loss_ratio', 'MLR', true),
        _MetricSpec('expense_ratio', 'Expense Ratio', true),
        _MetricSpec('premium_growth', 'Premium Growth', false),
        _MetricSpec('roe', 'ROE', false),
      ];
    }
    return const [
      _MetricSpec('combined_ratio', 'Combined Ratio', true),
      _MetricSpec('loss_ratio', 'Loss Ratio', true),
      _MetricSpec('expense_ratio', 'Expense Ratio', true),
      _MetricSpec('investment_yield', 'Investment Yield', false),
      _MetricSpec('premium_growth', 'Premium Growth', false),
      _MetricSpec('roe', 'ROE', false),
    ];
  }
}

class _Suggestion {
  final Map<String, dynamic> entry;
  final int score;
  _Suggestion(this.entry, this.score);
}

class _MetricSpec {
  final String code;
  final String label;
  final bool lowerIsBetter;
  const _MetricSpec(this.code, this.label, this.lowerIsBetter);
}
