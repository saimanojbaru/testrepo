import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Lightweight client-side cache for API responses.
///
/// Two layers:
/// * In-memory map (per app session) — instant
/// * SharedPreferences with TTL — survives restarts, freshness ~ default 10 min.
class CacheService {
  CacheService._();
  static final CacheService instance = CacheService._();

  static const _prefix = 'iip_cache_v1::';
  final Map<String, _Entry> _memory = {};

  Future<dynamic> read(String key) async {
    final mem = _memory[key];
    if (mem != null && !mem.isExpired) return mem.value;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('$_prefix$key');
    if (raw == null) return null;
    try {
      final parsed = jsonDecode(raw) as Map<String, dynamic>;
      final exp = (parsed['exp'] as num?)?.toInt() ?? 0;
      if (exp != 0 && exp < DateTime.now().millisecondsSinceEpoch) {
        await prefs.remove('$_prefix$key');
        return null;
      }
      _memory[key] =
          _Entry(parsed['v'], expiresAt: exp == 0 ? null : exp);
      return parsed['v'];
    } catch (_) {
      return null;
    }
  }

  Future<void> write(String key, dynamic value, {Duration? ttl}) async {
    final exp = ttl == null
        ? 0
        : DateTime.now().add(ttl).millisecondsSinceEpoch;
    _memory[key] = _Entry(value, expiresAt: exp == 0 ? null : exp);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      '$_prefix$key',
      jsonEncode({'v': value, 'exp': exp}),
    );
  }

  Future<void> evict(String key) async {
    _memory.remove(key);
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('$_prefix$key');
  }

  Future<void> clear() async {
    _memory.clear();
    final prefs = await SharedPreferences.getInstance();
    final keys = prefs.getKeys()
        .where((k) => k.startsWith(_prefix))
        .toList();
    for (final k in keys) {
      await prefs.remove(k);
    }
  }
}

class _Entry {
  final dynamic value;
  final int? expiresAt;
  _Entry(this.value, {this.expiresAt});

  bool get isExpired =>
      expiresAt != null && expiresAt! < DateTime.now().millisecondsSinceEpoch;
}
