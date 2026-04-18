import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AuthState {
  AuthState({this.baseUrl, this.token, this.expiresAt});

  final String? baseUrl;
  final String? token;
  final DateTime? expiresAt;

  bool get isAuthenticated {
    if (baseUrl == null || token == null) return false;
    if (expiresAt != null && expiresAt!.isBefore(DateTime.now())) return false;
    return true;
  }

  AuthState copyWith({String? baseUrl, String? token, DateTime? expiresAt}) =>
      AuthState(
        baseUrl: baseUrl ?? this.baseUrl,
        token: token ?? this.token,
        expiresAt: expiresAt ?? this.expiresAt,
      );
}

class AuthController extends StateNotifier<AuthState> {
  AuthController() : super(AuthState()) {
    _load();
  }

  static const _kBaseUrl = 'auth.baseUrl';
  static const _kToken = 'auth.token';
  static const _kExpires = 'auth.expires';

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final baseUrl = prefs.getString(_kBaseUrl);
    final token = prefs.getString(_kToken);
    final expiresMs = prefs.getInt(_kExpires);
    state = AuthState(
      baseUrl: baseUrl,
      token: token,
      expiresAt: expiresMs == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(expiresMs),
    );
  }

  Future<void> save({
    required String baseUrl,
    required String token,
    required DateTime expiresAt,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kBaseUrl, baseUrl);
    await prefs.setString(_kToken, token);
    await prefs.setInt(_kExpires, expiresAt.millisecondsSinceEpoch);
    state = AuthState(baseUrl: baseUrl, token: token, expiresAt: expiresAt);
  }

  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_kBaseUrl);
    await prefs.remove(_kToken);
    await prefs.remove(_kExpires);
    state = AuthState();
  }
}

final authProvider = StateNotifierProvider<AuthController, AuthState>(
  (ref) => AuthController(),
);
