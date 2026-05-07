import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../models/company.dart';
import '../models/insight.dart';
import 'cache_service.dart';

/// Talks to the FastAPI backend.
///
/// Resolution rules:
/// * Web/desktop debug: ``http://localhost:8000``
/// * Android emulator:   ``http://10.0.2.2:8000``
/// * Real device / release: override via [baseUrlOverride] or pass at runtime.
class ApiService {
  ApiService._();
  static final ApiService instance = ApiService._();

  static String? baseUrlOverride;

  String get baseUrl {
    if (baseUrlOverride != null && baseUrlOverride!.isNotEmpty) {
      return baseUrlOverride!;
    }
    if (kIsWeb) return 'http://localhost:8000';
    if (Platform.isAndroid) return 'http://10.0.2.2:8000';
    return 'http://localhost:8000';
  }

  final http.Client _http = http.Client();

  // ---- Generic request helpers --------------------------------------------

  Future<dynamic> _get(String path,
      {Duration ttl = const Duration(minutes: 10),
      bool useCache = true}) async {
    final url = '$baseUrl$path';
    final cacheKey = 'GET::$url';
    if (useCache) {
      final cached = await CacheService.instance.read(cacheKey);
      if (cached != null) return cached;
    }
    try {
      final r = await _http
          .get(Uri.parse(url), headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 12));
      if (r.statusCode >= 200 && r.statusCode < 300) {
        final decoded = jsonDecode(r.body);
        await CacheService.instance.write(cacheKey, decoded, ttl: ttl);
        return decoded;
      }
      throw ApiException('GET $path failed: ${r.statusCode}');
    } on TimeoutException {
      throw ApiException('Request timed out. Check your connection.');
    } catch (e) {
      throw ApiException('Network error: $e');
    }
  }

  Future<dynamic> _post(String path, Map<String, dynamic> body,
      {Duration ttl = const Duration(minutes: 5),
      bool useCache = true}) async {
    final url = '$baseUrl$path';
    final cacheKey = 'POST::$url::${jsonEncode(body)}';
    if (useCache) {
      final cached = await CacheService.instance.read(cacheKey);
      if (cached != null) return cached;
    }
    try {
      final r = await _http
          .post(
            Uri.parse(url),
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
            },
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 18));
      if (r.statusCode >= 200 && r.statusCode < 300) {
        final decoded = jsonDecode(r.body);
        await CacheService.instance.write(cacheKey, decoded, ttl: ttl);
        return decoded;
      }
      throw ApiException('POST $path failed: ${r.statusCode}');
    } on TimeoutException {
      throw ApiException('Request timed out. Check your connection.');
    } catch (e) {
      throw ApiException('Network error: $e');
    }
  }

  // ---- Endpoints -----------------------------------------------------------

  Future<MarketPulse> dashboardPulse({bool refresh = false}) async {
    final j = await _get('/dashboard/pulse',
        useCache: !refresh, ttl: const Duration(minutes: 30));
    return MarketPulse.fromJson(j as Map<String, dynamic>);
  }

  Future<List<NewsItem>> dashboardTopNews({int limit = 6}) async {
    final j = await _get('/dashboard/top-news?limit=$limit',
        ttl: const Duration(minutes: 15));
    return ((j['items'] as List?) ?? [])
        .map((e) => NewsItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<CompanyAnalysis> analyzeCompany(String query,
      {bool refresh = false}) async {
    final j = await _get('/company/${Uri.encodeComponent(query)}',
        useCache: !refresh, ttl: const Duration(minutes: 30));
    return CompanyAnalysis.fromJson(j as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> searchCompanies(String q) async {
    final j = await _get('/company/search?q=${Uri.encodeQueryComponent(q)}',
        ttl: const Duration(minutes: 5));
    return j as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> autoCompare(String query,
      {bool refresh = false}) async {
    return await _get('/compare/auto?q=${Uri.encodeQueryComponent(query)}',
        useCache: !refresh, ttl: const Duration(minutes: 20));
  }

  Future<Map<String, dynamic>> compare(List<String> tickers,
      {bool refresh = false}) async {
    return await _post('/compare', {'companies': tickers},
        useCache: !refresh, ttl: const Duration(minutes: 20));
  }

  Future<Map<String, dynamic>> updates({String? category, int limit = 40}) async {
    final qp = StringBuffer('?limit=$limit');
    if (category != null && category != 'All') {
      qp.write('&category=${Uri.encodeQueryComponent(category)}');
    }
    return await _get('/updates$qp', ttl: const Duration(minutes: 10));
  }

  Future<Map<String, dynamic>> knowledgeList({String? framework}) async {
    final qp = framework != null && framework != 'All'
        ? '?framework=${Uri.encodeQueryComponent(framework)}'
        : '';
    return await _get('/knowledge$qp', ttl: const Duration(hours: 6));
  }

  Future<List<KnowledgeArticle>> knowledgeSearch(String q) async {
    final j = await _get('/knowledge/search?q=${Uri.encodeQueryComponent(q)}',
        ttl: const Duration(hours: 1));
    return ((j['results'] as List?) ?? [])
        .map((e) => KnowledgeArticle.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<KnowledgeArticle> knowledgeDetail(String slug) async {
    final j =
        await _get('/knowledge/$slug', ttl: const Duration(hours: 6));
    return KnowledgeArticle.fromJson(j as Map<String, dynamic>);
  }
}

class ApiException implements Exception {
  final String message;
  ApiException(this.message);
  @override
  String toString() => message;
}
