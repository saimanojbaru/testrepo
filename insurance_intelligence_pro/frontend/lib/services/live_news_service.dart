import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:xml/xml.dart' as xml;

import '../models/insight.dart';

/// Fetches live insurance-industry news directly from public RSS feeds
/// — **no backend deployment required**.
///
/// Android can hit these HTTPS endpoints natively (no CORS), so we
/// parallel-fan-out across all of them, parse XML, classify category +
/// impact heuristically, and return a merged feed deduped by title.
///
/// Failures are silent. Never throws.
class LiveNewsService {
  LiveNewsService._();
  static final LiveNewsService instance = LiveNewsService._();

  /// Public RSS sources covering the US insurance landscape.
  static const List<_Feed> _feeds = [
    _Feed('Insurance Journal',
        'https://www.insurancejournal.com/news/feed/'),
    _Feed('SEC Press Releases',
        'https://www.sec.gov/news/pressreleases.rss'),
    _Feed('Reinsurance News', 'https://www.reinsurancene.ws/feed/'),
    _Feed('Artemis', 'https://www.artemis.bm/news/feed/'),
    _Feed('AM Best', 'https://news.ambest.com/rss/companynews.rss'),
    _Feed('NAIC News', 'https://content.naic.org/feeds/newsroom.rss'),
  ];

  static const Map<String, List<String>> _categoryKeywords = {
    'Regulation': ['NAIC', 'regulation', 'regulatory', 'rule', 'filing', 'SEC', 'commissioner'],
    'Catastrophe': ['hurricane', 'wildfire', 'earthquake', 'catastrophe', 'cat bond', 'tornado', 'flood'],
    'Reinsurance': ['reinsurance', 'retro', 'Lloyd', 'treaty', 'cession'],
    'Earnings': ['earnings', 'quarter', 'guidance', 'revenue'],
    'M&A': ['acquire', 'merger', 'deal', 'acquisition', 'stake'],
    'Climate': ['climate', 'ESG', 'sustainability', 'decarbon'],
    'Cyber': ['cyber', 'ransomware', 'breach'],
    'Health': ['medicare', 'medicaid', 'ACA', 'MLR', 'health insurance'],
  };

  static const Set<String> _highImpact = {
    'NAIC', 'SEC', 'lawsuit', 'downgrade', 'rating', 'guidance',
    'ratings cut', 'default', 'bankrupt'
  };
  static const Set<String> _lowImpact = {
    'podcast', 'appoint', 'promotion', 'hire', 'honored'
  };

  Map<String, List<NewsItem>>? _cache;
  DateTime? _cachedAt;

  /// Returns a merged, deduped, time-ordered list of news items.
  Future<List<NewsItem>> fetchAll({
    bool refresh = false,
    Duration maxAge = const Duration(minutes: 10),
  }) async {
    if (!refresh &&
        _cache != null &&
        _cachedAt != null &&
        DateTime.now().difference(_cachedAt!) < maxAge) {
      return _flatten(_cache!);
    }
    final results = <String, List<NewsItem>>{};
    final futures = _feeds
        .map((f) => _fetchOne(f).then((items) => results[f.name] = items));
    await Future.wait(futures);
    _cache = results;
    _cachedAt = DateTime.now();
    return _flatten(results);
  }

  Future<List<NewsItem>> fetchByCategory(
    String category, {
    bool refresh = false,
  }) async {
    final all = await fetchAll(refresh: refresh);
    if (category == 'All') return all;
    return all.where((n) => n.category == category).toList();
  }

  Future<List<NewsItem>> _fetchOne(_Feed feed) async {
    try {
      final r = await http.get(
        Uri.parse(feed.url),
        headers: {
          'User-Agent':
              'InsuranceIntelligencePro/1.0 (live news ingest; contact@example.com)',
          'Accept':
              'application/rss+xml, application/xml, application/atom+xml, text/xml; q=0.9, */*; q=0.5',
        },
      ).timeout(const Duration(seconds: 8));
      if (r.statusCode != 200) return const [];
      return _parseFeed(feed.name, r.body);
    } catch (e) {
      if (kDebugMode) debugPrint('LiveNews fetch failed [${feed.name}]: $e');
      return const [];
    }
  }

  List<NewsItem> _parseFeed(String source, String body) {
    try {
      final doc = xml.XmlDocument.parse(body);
      final items = doc.findAllElements('item').toList();
      final atomEntries = doc.findAllElements('entry').toList();
      final out = <NewsItem>[];
      for (final el in items) {
        final ni = _itemToNewsItem(el, source);
        if (ni != null) out.add(ni);
      }
      for (final el in atomEntries) {
        final ni = _entryToNewsItem(el, source);
        if (ni != null) out.add(ni);
      }
      return out.take(40).toList();
    } catch (e) {
      if (kDebugMode) debugPrint('LiveNews parse failed [$source]: $e');
      return const [];
    }
  }

  NewsItem? _itemToNewsItem(xml.XmlElement el, String source) {
    final title = _firstText(el, 'title');
    if (title.isEmpty) return null;
    final link = _firstText(el, 'link');
    final desc = _firstText(el, 'description');
    final pubDate = _firstText(el, 'pubDate');
    final body = '$title $desc';
    return NewsItem(
      title: title,
      url: link,
      source: source,
      published: _normalizeDate(pubDate),
      summary: _stripHtml(desc).trim(),
      category: _classify(body),
      impact: _impact(body),
    );
  }

  NewsItem? _entryToNewsItem(xml.XmlElement el, String source) {
    final title = _firstText(el, 'title');
    if (title.isEmpty) return null;
    String link = '';
    for (final l in el.findElements('link')) {
      final href = l.getAttribute('href');
      if (href != null && href.isNotEmpty) {
        link = href;
        break;
      }
    }
    final summary = _firstText(el, 'summary').isEmpty
        ? _firstText(el, 'content')
        : _firstText(el, 'summary');
    final updated = _firstText(el, 'updated').isEmpty
        ? _firstText(el, 'published')
        : _firstText(el, 'updated');
    final body = '$title $summary';
    return NewsItem(
      title: title,
      url: link,
      source: source,
      published: _normalizeDate(updated),
      summary: _stripHtml(summary).trim(),
      category: _classify(body),
      impact: _impact(body),
    );
  }

  String _firstText(xml.XmlElement el, String name) {
    final found = el.findElements(name);
    if (found.isEmpty) return '';
    return found.first.innerText.trim();
  }

  String _classify(String text) {
    final t = text.toLowerCase();
    for (final entry in _categoryKeywords.entries) {
      for (final kw in entry.value) {
        if (t.contains(kw.toLowerCase())) return entry.key;
      }
    }
    return 'General';
  }

  String _impact(String text) {
    final t = text.toLowerCase();
    if (_highImpact.any((kw) => t.contains(kw.toLowerCase()))) return 'High';
    if (_lowImpact.any((kw) => t.contains(kw.toLowerCase()))) return 'Low';
    return 'Medium';
  }

  String _stripHtml(String s) {
    final sb = StringBuffer();
    var inTag = false;
    for (final r in s.runes) {
      final c = String.fromCharCode(r);
      if (c == '<') {
        inTag = true;
        continue;
      }
      if (c == '>') {
        inTag = false;
        continue;
      }
      if (!inTag) sb.write(c);
    }
    final cleaned = sb
        .toString()
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&nbsp;', ' ');
    return cleaned.length > 320 ? '${cleaned.substring(0, 320)}…' : cleaned;
  }

  String _normalizeDate(String raw) {
    if (raw.isEmpty) return '';
    try {
      final iso = DateTime.tryParse(raw);
      if (iso != null) return iso.toIso8601String();
      final cleaned = raw
          .replaceAll(RegExp(r'\s+(GMT|UTC|EST|EDT|CST|CDT|PST|PDT)$'), '');
      return DateTime.tryParse(cleaned)?.toIso8601String() ?? raw;
    } catch (_) {
      return raw;
    }
  }

  List<NewsItem> _flatten(Map<String, List<NewsItem>> bySource) {
    final all = <NewsItem>[];
    for (final list in bySource.values) {
      all.addAll(list);
    }
    final seen = <String>{};
    final deduped = <NewsItem>[];
    for (final n in all) {
      final key = n.title.trim().toLowerCase();
      if (seen.contains(key)) continue;
      seen.add(key);
      deduped.add(n);
    }
    deduped.sort((a, b) =>
        (b.published ?? '').compareTo(a.published ?? ''));
    return deduped;
  }
}

class _Feed {
  final String name;
  final String url;
  const _Feed(this.name, this.url);
}
