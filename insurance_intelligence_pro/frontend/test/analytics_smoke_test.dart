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

  // -------- Standards catalog (SSAP / ASC 944 / PCAOB) ---------------------

  test('SSAP catalog loads with 25 chapters from 2026 NAIC manual', () {
    final cat = LocalData.instance.catalogFor('SSAP');
    expect(cat.framework, 'SSAP');
    expect(cat.standards.length, greaterThanOrEqualTo(20));
    expect(cat.manual, contains('2026 NAIC'));
    expect(cat.publisher, contains('NAIC'));
  });

  test('SSAP 2R deep dive matches the reference schema example', () {
    final cat = LocalData.instance.catalogFor('SSAP');
    final ssap2r = cat.standards.firstWhere((s) => s.id == 'SSAP-2R');
    // Section 1
    expect(ssap2r.chapterContent, contains('SSAP'));
    // Section 2 — need
    expect(ssap2r.need, contains('Liquidity'));
    // Section 3 — evolution (must include 2026 update)
    expect(
        ssap2r.evolution.any((e) =>
            e.year == '2026' && e.change.contains('Working Capital')),
        isTrue);
    // Section 4 — GAAP comparison must include the three exemplar rows
    final aspects = ssap2r.gaapComparison.map((r) => r.aspect).toList();
    expect(aspects, contains('Goal'));
    expect(aspects, contains('Cash Equivalents definition'));
    expect(aspects, contains('Bank Overdrafts'));
  });

  test('ASC 944 catalog covers all sub-topics', () {
    final cat = LocalData.instance.catalogFor('ASC944');
    expect(cat.framework, 'ASC944');
    expect(cat.standards.length, greaterThanOrEqualTo(12));
    final ids = cat.standards.map((s) => s.id).toList();
    expect(ids, contains('ASC-944-30')); // DAC
    expect(ids, contains('ASC-944-40')); // Reserves
    expect(ids, contains('ASC-944-605')); // Premium revenue
  });

  test('PCAOB catalog includes core insurance-audit AS', () {
    final cat = LocalData.instance.catalogFor('PCAOB');
    expect(cat.framework, 'PCAOB');
    expect(cat.standards.length, greaterThanOrEqualTo(10));
    final ids = cat.standards.map((s) => s.id).toList();
    expect(ids, contains('PCAOB-AS-2501')); // Estimates / fair value
    expect(ids, contains('PCAOB-AS-1210')); // Specialists
    expect(ids, contains('PCAOB-AS-3101')); // Auditor's report w/ CAMs
  });

  test('Every standard has the four required sections populated', () {
    for (final framework in ['SSAP', 'ASC944', 'PCAOB']) {
      final cat = LocalData.instance.catalogFor(framework);
      for (final s in cat.standards) {
        expect(s.chapterContent, isNotEmpty,
            reason: '${s.id} missing chapter content');
        expect(s.need, isNotEmpty, reason: '${s.id} missing need');
        expect(s.evolution, isNotEmpty,
            reason: '${s.id} missing evolution');
        expect(s.gaapComparison, isNotEmpty,
            reason: '${s.id} missing gaap comparison');
      }
    }
  });

  test('Standard searchCorpus matches text in all four sections', () {
    final cat = LocalData.instance.catalogFor('SSAP');
    final ssap2r = cat.standards.firstWhere((s) => s.id == 'SSAP-2R');
    expect(ssap2r.searchCorpus, contains('liquidity'));
    expect(ssap2r.searchCorpus, contains('working capital finance'));
    expect(ssap2r.searchCorpus, contains('overdraft'));
  });
}
