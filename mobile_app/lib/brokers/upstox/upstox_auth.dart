import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'upstox_config.dart';

class UpstoxCredentials {
  const UpstoxCredentials({
    required this.apiKey,
    required this.apiSecret,
    required this.redirectUri,
  });

  final String apiKey;
  final String apiSecret;
  final String redirectUri;

  Map<String, String> toMap() => {
        'api_key': apiKey,
        'api_secret': apiSecret,
        'redirect_uri': redirectUri,
      };

  factory UpstoxCredentials.fromMap(Map<String, String> m) =>
      UpstoxCredentials(
        apiKey: m['api_key'] ?? '',
        apiSecret: m['api_secret'] ?? '',
        redirectUri: m['redirect_uri'] ?? '',
      );

  bool get isComplete =>
      apiKey.isNotEmpty && apiSecret.isNotEmpty && redirectUri.isNotEmpty;
}

class UpstoxSession {
  UpstoxSession({required this.accessToken, required this.issuedAt});
  final String accessToken;
  final DateTime issuedAt;

  /// Upstox access tokens expire daily at 03:30 AM IST.
  /// Treat tokens older than 18h as stale (forces re-login before market open).
  bool get isStale =>
      DateTime.now().difference(issuedAt).inMinutes > 18 * 60;
}

abstract class TokenStore {
  Future<void> saveCredentials(UpstoxCredentials c);
  Future<UpstoxCredentials?> loadCredentials();
  Future<void> saveSession(UpstoxSession s);
  Future<UpstoxSession?> loadSession();
  Future<void> clearSession();
  Future<void> clearAll();
}

class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _kApiKey = 'upstox_api_key';
  static const _kApiSecret = 'upstox_api_secret';
  static const _kRedirect = 'upstox_redirect_uri';
  static const _kToken = 'upstox_access_token';
  static const _kIssuedAt = 'upstox_issued_at';

  @override
  Future<void> saveCredentials(UpstoxCredentials c) async {
    await _storage.write(key: _kApiKey, value: c.apiKey);
    await _storage.write(key: _kApiSecret, value: c.apiSecret);
    await _storage.write(key: _kRedirect, value: c.redirectUri);
  }

  @override
  Future<UpstoxCredentials?> loadCredentials() async {
    final k = await _storage.read(key: _kApiKey);
    final s = await _storage.read(key: _kApiSecret);
    final r = await _storage.read(key: _kRedirect);
    if (k == null || s == null || r == null) return null;
    return UpstoxCredentials(apiKey: k, apiSecret: s, redirectUri: r);
  }

  @override
  Future<void> saveSession(UpstoxSession s) async {
    await _storage.write(key: _kToken, value: s.accessToken);
    await _storage.write(
        key: _kIssuedAt, value: s.issuedAt.toIso8601String());
  }

  @override
  Future<UpstoxSession?> loadSession() async {
    final t = await _storage.read(key: _kToken);
    final ts = await _storage.read(key: _kIssuedAt);
    if (t == null || ts == null) return null;
    return UpstoxSession(accessToken: t, issuedAt: DateTime.parse(ts));
  }

  @override
  Future<void> clearSession() async {
    await _storage.delete(key: _kToken);
    await _storage.delete(key: _kIssuedAt);
  }

  @override
  Future<void> clearAll() async {
    await _storage.delete(key: _kApiKey);
    await _storage.delete(key: _kApiSecret);
    await _storage.delete(key: _kRedirect);
    await clearSession();
  }
}

class UpstoxAuth {
  UpstoxAuth({Dio? dio}) : _dio = dio ?? Dio();

  final Dio _dio;

  /// Builds the browser URL the user must open to authorize the app.
  Uri buildAuthorizationUrl(UpstoxCredentials c) {
    return Uri.parse(UpstoxConfig.authBase).replace(queryParameters: {
      'response_type': 'code',
      'client_id': c.apiKey,
      'redirect_uri': c.redirectUri,
    });
  }

  /// Exchanges an authorization code for an access token.
  Future<UpstoxSession> exchange({
    required UpstoxCredentials credentials,
    required String code,
  }) async {
    final resp = await _dio.post(
      UpstoxConfig.tokenUrl,
      options: Options(
        contentType: Headers.formUrlEncodedContentType,
        headers: {'Accept': 'application/json'},
      ),
      data: {
        'code': code.trim(),
        'client_id': credentials.apiKey,
        'client_secret': credentials.apiSecret,
        'redirect_uri': credentials.redirectUri,
        'grant_type': 'authorization_code',
      },
    );
    final data = resp.data;
    final token = data is Map ? data['access_token'] as String? : null;
    if (token == null || token.isEmpty) {
      throw UpstoxAuthException(
          'Upstox did not return an access token. Check credentials + code.');
    }
    return UpstoxSession(accessToken: token, issuedAt: DateTime.now());
  }
}

class UpstoxAuthException implements Exception {
  UpstoxAuthException(this.message);
  final String message;

  @override
  String toString() => 'UpstoxAuthException: $message';
}
