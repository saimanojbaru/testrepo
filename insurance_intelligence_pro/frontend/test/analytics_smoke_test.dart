import 'dart:io';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:insurance_intelligence_pro/data/local_data.dart';
import 'package:insurance_intelligence_pro/models/insight.dart';
import 'package:insurance_intelligence_pro/services/analytics_service.dart';

/// Functional smoke tests covering every screen's data path.
///
/// Runs on the host (no emulator needed) and exercises the same
/// engines the released APK uses. Green CI means working features.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    final basePath = Directory.current.path;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMessageHandler('flutter/assets', (message) async {
      final key = utf8.decode(message!.buffer.asUint8List());
      final file = File('$basePath/$key');
      if (!file.existsSync()) return null;
      final bytes = await file.readAsBytes();
      return ByteData.view(Uint8List.fromList(bytes).buffer);
    });
    await LocalData.instance.load();
  });

  test('Dashboard pulse returns insights and indicators', () {
    final pulse = AnalyticsService.instance.pulse();
    expect(pulse['headline'], isNotEmpty);
    expect((pulse['insights'] as List).length, greaterThanOrEqualTo(3));
    expect((pulse['indicators'] as List).length, greaterThanOrEqualTo(3));
  });

  test('Top news returns at least 5 curated headlines', () {
    final news = AnalyticsService.instance.topNews(limit: 5);
    expect(news.length, 5);
    expect(news.first['title'], isNotEmpty);
  });

  test('Company analysis: PGR has KPIs, risk radar, insights', () {
    final a = AnalyticsService.instance.analyzeCompany('PGR');
    expect(a.company.ticker, 'PGR');
    expect(a.company.insurerType, 'P&C');
    expect(a.score, greaterThan(0));
    expect(a.kpis.primary, isNotEmpty);
    expect(a.kpis.primary.any((k) => k.code == 'combined_ratio'), isTrue);
    expect(a.riskRadar.factors, isNotEmpty);
    expect(a.insights, isNotEmpty);
    expect(a.series, isNotEmpty);
  });

  test('Every KPI carries filing source + formula provenance', () {
    final a = AnalyticsService.instance.analyzeCompany('PGR');
    final cr = a.kpis.primary.firstWhere((k) => k.code == 'combined_ratio');
    expect(cr.source, isNotNull,
        reason: 'KPIs must have a filing source for traceability.');
    expect(cr.source!.form, '10-K');
    expect(cr.source!.fiscalYear, contains('FY'));
    expect(cr.source!.url, contains('sec.gov'));
    final lr = a.kpis.primary.firstWhere((k) => k.code == 'loss_ratio');
    expect(lr.formula, isNotNull,
        reason: 'Loss Ratio must surface its formula.');
    expect(lr.formula!.expression, contains('Earned Premium'));
    expect(lr.formula!.numeratorValue, contains('\$'));
  });

  test('KPI carries a knowledgeSlug that resolves to an article', () {
    final a = AnalyticsService.instance.analyzeCompany('PGR');
    final cr = a.kpis.primary.firstWhere((k) => k.code == 'combined_ratio');
    expect(cr.knowledgeSlug, isNotNull);
    final raw = AnalyticsService.instance
        .knowledge()
        .firstWhere((art) => art['slug'] == cr.knowledgeSlug,
            orElse: () => <String, dynamic>{});
    expect(raw, isNotEmpty,
        reason: 'KPI knowledgeSlug must resolve to an article.');
  });

  test('Knowledge has at least 25 articles spanning core FSLIs', () {
    final all = AnalyticsService.instance.knowledge();
    expect(all.length, greaterThanOrEqualTo(25));
  });

  test('Search "cash" returns the Cash & Cash Equivalents FSLI article', () {
    final hits = AnalyticsService.instance.knowledgeSearch('cash');
    expect(hits, isNotEmpty,
        reason: 'Search must find articles for FSLI keywords.');
    final hasCash = hits
        .any((a) => a['slug'].toString().contains('cash'));
    expect(hasCash, isTrue);
  });

  test('Search "reserves" returns multiple deep articles', () {
    final hits = AnalyticsService.instance.knowledgeSearch('reserves');
    expect(hits.length, greaterThanOrEqualTo(2));
  });

  test('Search "DAC" finds the deferred-acquisition-costs article', () {
    final hits = AnalyticsService.instance.knowledgeSearch('DAC');
    expect(hits.any((a) => a['slug'] == 'dac-deferred-acquisition-costs'),
        isTrue);
  });

  test('Multi-token AND search ("life LDTI") narrows correctly', () {
    final hits = AnalyticsService.instance.knowledgeSearch('life LDTI');
    expect(hits, isNotEmpty);
    // All hits must mention both tokens somewhere.
    for (final h in hits) {
      final ka = KnowledgeArticle.fromJson(Map<String, dynamic>.from(h));
      expect(ka.searchCorpus, contains('life'));
      expect(ka.searchCorpus, contains('ldti'));
    }
  });

  test('Articles parse sections, fsli_table, references', () {
    final all = AnalyticsService.instance.knowledge();
    final cash = KnowledgeArticle.fromJson(
        Map<String, dynamic>.from(all.firstWhere((a) => a['slug'] == 'cash-and-equivalents')));
    expect(cash.sections, isNotEmpty);
    expect(cash.fsliTable, isNotEmpty);
    expect(cash.references, isNotEmpty);
    expect(cash.depth, 'deep');
    expect(cash.lastUpdated, isNotNull);
    final fsliRow = cash.fsliTable.first;
    expect(fsliRow.gaap, isNotEmpty);
    expect(fsliRow.stat, isNotEmpty);
    expect(fsliRow.delta, isNotEmpty);
  });

  test('Search corpus traverses every field for deep matches', () {
    // "schedule p" appears in body_md / FSLI table — make sure search finds it.
    final hits = AnalyticsService.instance.knowledgeSearch('schedule p');
    expect(hits, isNotEmpty);
  });

  test('Compare ranks PGR/TRV/CB and produces a verdict', () {
    final c = AnalyticsService.instance.compare(['PGR', 'TRV', 'CB']);
    final companies = c['companies'] as List;
    final metrics = c['metrics'] as List;
    expect(companies.length, 3);
    expect(metrics.length, greaterThanOrEqualTo(5));
    expect(c['verdict'], isNotEmpty);
  });

  test('Auto-peer for PGR returns P&C peers with ranked metrics', () {
    final c = AnalyticsService.instance.autoCompare('PGR');
    expect(c['insurer_type'], 'P&C');
    expect((c['companies'] as List).length, greaterThanOrEqualTo(4));
  });

  test('News filtered by category', () {
    final reg = AnalyticsService.instance.news(category: 'Regulation');
    final all = (reg['items'] as List);
    expect(all.every((n) => n['category'] == 'Regulation'), isTrue);
  });

  test('Unknown company falls back to a helpful empty analysis', () {
    final a = AnalyticsService.instance.analyzeCompany('NOTAREALCO');
    expect(a.score, 0);
    expect(a.headline, contains('NOTAREALCO'));
    expect(a.insights, isNotEmpty);
  });
}
