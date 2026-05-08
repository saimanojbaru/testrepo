import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Tiny REST client for the optional FastAPI backend.
///
/// The Flutter app is offline-first: every screen renders immediately
/// from bundled curated data. When `ApiService.instance.baseUrl` is set
/// (via `--dart-define=API_BASE_URL=...` at build time, or via the
/// in-app Settings sheet which calls [setBaseUrl]), the relevant data
/// providers will *also* try the backend and overlay live results
/// (latest news, fresh SEC filings, NAIC bulletins) on top of the
/// curated baseline.
///
/// Failures are silent. Offline always works.
class ApiService {
  ApiService._();
  static final ApiService instance = ApiService._();

  static const _prefsKey = 'iip_api_base_url';
  static const String _buildTimeBase =
      String.fromEnvironment('API_BASE_URL', defaultValue: '');

  String? _baseUrl;
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;
    final prefs = await SharedPreferences.getInstance();
    _baseUrl = prefs.getString(_prefsKey) ??
        (_buildTimeBase.isEmpty ? null : _buildTimeBase);
    _loaded = true;
  }

  String? get baseUrl => _baseUrl;
  bool get isConfigured => (_baseUrl ?? '').trim().isNotEmpty;

  Future<void> setBaseUrl(String? url) async {
    final prefs = await SharedPreferences.getInstance();
    final clean = (url ?? '').trim();
    if (clean.isEmpty) {
      await prefs.remove(_prefsKey);
      _baseUrl = null;
    } else {
      await prefs.setString(_prefsKey, clean);
      _baseUrl = clean;
    }
  }

  /// Probe the backend's /healthz endpoint. Returns true if reachable.
  Future<bool> probe() async {
    final base = _baseUrl;
    if (base == null || base.isEmpty) return false;
    try {
      final r = await http
          .get(Uri.parse('$base/healthz'),
              headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 4));
      return r.statusCode >= 200 && r.statusCode < 300;
    } catch (e) {
      if (kDebugMode) debugPrint('ApiService.probe failed: $e');
      return false;
    }
  }

  /// Fetch live industry news. Returns [] on any failure (caller falls
  /// back to curated headlines).
  Future<List<Map<String, dynamic>>> liveNews({
    String? category,
    int limit = 40,
  }) async {
    final base = _baseUrl;
    if (base == null || base.isEmpty) return const [];
    final qp = StringBuffer('?limit=$limit');
    if (category != null && category != 'All') {
      qp.write('&category=${Uri.encodeQueryComponent(category)}');
    }
    try {
      final r = await http
          .get(Uri.parse('$base/updates$qp'))
          .timeout(const Duration(seconds: 6));
      if (r.statusCode != 200) return const [];
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      final items = (body['items'] as List?) ?? const [];
      return items.cast<Map<String, dynamic>>();
    } catch (_) {
      return const [];
    }
  }

  /// Fetch the live dashboard pulse. Returns null on failure.
  Future<Map<String, dynamic>?> livePulse() async {
    final base = _baseUrl;
    if (base == null || base.isEmpty) return null;
    try {
      final r = await http
          .get(Uri.parse('$base/dashboard/pulse'))
          .timeout(const Duration(seconds: 6));
      if (r.statusCode != 200) return null;
      return jsonDecode(r.body) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  /// Fetch live company analysis (real SEC EDGAR data when backend is
  /// available). Returns null on failure.
  Future<Map<String, dynamic>?> liveCompany(String query) async {
    final base = _baseUrl;
    if (base == null || base.isEmpty) return null;
    try {
      final r = await http
          .get(Uri.parse('$base/company/${Uri.encodeComponent(query)}'))
          .timeout(const Duration(seconds: 10));
      if (r.statusCode != 200) return null;
      return jsonDecode(r.body) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }
}
