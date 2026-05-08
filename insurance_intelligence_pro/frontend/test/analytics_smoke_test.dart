import 'dart:io';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:insurance_intelligence_pro/data/local_data.dart';
import 'package:insurance_intelligence_pro/services/analytics_service.dart';

/// Functional smoke tests covering every screen's data path.
///
/// These run on the host (no emulator needed) and exercise the same
/// engines the released APK uses, so a green CI = working features.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    // Make rootBundle.loadString resolve real asset files from disk.
    final basePath = Directory.current.path;
    ServicesBinding.instance.defaultBinaryMessenger
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

  test('Company analysis: MET classified as Life with persistency KPI', () {
    final a = AnalyticsService.instance.analyzeCompany('MET');
    expect(a.company.insurerType, 'Life');
    expect(a.kpis.primary.any((k) => k.code == 'persistency'), isTrue);
    expect(a.kpis.primary.any((k) => k.code == 'investment_yield'), isTrue);
  });

  test('Company analysis: UNH classified as Health with MLR', () {
    final a = AnalyticsService.instance.analyzeCompany('UNH');
    expect(a.company.insurerType, 'Health');
    expect(a.kpis.primary.any((k) => k.code == 'medical_loss_ratio'), isTrue);
  });

  test('Search by name resolves to ticker', () {
    final r = AnalyticsService.instance.resolveCompany('Travelers');
    expect(r, isNotNull);
    expect(r!['ticker'], 'TRV');
  });

  test('Suggest is fast and ranked by prefix match', () {
    final s = AnalyticsService.instance.suggest('PR', limit: 5);
    expect(s, isNotEmpty);
    expect(s.first['ticker'].toString(), startsWith('PR'));
  });

  test('Compare ranks PGR/TRV/CB and produces a verdict', () {
    final c = AnalyticsService.instance.compare(['PGR', 'TRV', 'CB']);
    final companies = c['companies'] as List;
    final metrics = c['metrics'] as List;
    expect(companies.length, 3);
    expect(metrics.length, greaterThanOrEqualTo(5));
    expect(c['verdict'], isNotEmpty);
    final crMetric = metrics.firstWhere((m) => m['code'] == 'combined_ratio');
    final values = (crMetric['values'] as List).cast<Map>();
    expect(values.every((v) => v['rank'] != null && v['value'] != null), isTrue);
  });

  test('Auto-peer for PGR returns P&C peers', () {
    final c = AnalyticsService.instance.autoCompare('PGR');
    expect(c['insurer_type'], 'P&C');
    expect((c['companies'] as List).length, greaterThanOrEqualTo(4));
  });

  test('News filtered by category', () {
    final reg = AnalyticsService.instance.news(category: 'Regulation');
    final all = (reg['items'] as List);
    expect(all.every((n) => n['category'] == 'Regulation'), isTrue);
  });

  test('Knowledge has ASC944 article and is searchable', () {
    final all = AnalyticsService.instance.knowledge();
    expect(all.any((a) => a['framework'] == 'ASC944'), isTrue);
    final hits = AnalyticsService.instance.knowledgeSearch('LDTI');
    expect(hits, isNotEmpty);
  });

  test('Unknown company falls back to a helpful empty analysis', () {
    final a = AnalyticsService.instance.analyzeCompany('NOTAREALCO');
    expect(a.score, 0);
    expect(a.headline, contains('NOTAREALCO'));
    expect(a.insights, isNotEmpty);
  });
}
