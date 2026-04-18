import 'package:dio/dio.dart';

import 'models.dart';

class ApiException implements Exception {
  ApiException(this.message);
  final String message;
  @override
  String toString() => message;
}

class ApiClient {
  ApiClient(this.baseUrl, {this.token})
      : _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 8),
          receiveTimeout: const Duration(seconds: 8),
          headers: token == null ? null : {'Authorization': 'Bearer $token'},
        ));

  final String baseUrl;
  final String? token;
  final Dio _dio;

  Uri wsUri(String path) {
    final uri = Uri.parse(baseUrl);
    final scheme = uri.scheme == 'https' ? 'wss' : 'ws';
    return Uri(
      scheme: scheme,
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
      path: path,
      queryParameters: token == null ? null : {'token': token},
    );
  }

  Future<({String token, DateTime expiresAt})> login({
    required String deviceId,
    required String sharedSecret,
  }) async {
    try {
      final res = await _dio.post('/login', data: {
        'device_id': deviceId,
        'shared_secret': sharedSecret,
      });
      return (
        token: res.data['token'] as String,
        expiresAt: DateTime.parse(res.data['expires_at'] as String),
      );
    } on DioException catch (e) {
      throw ApiException(_extract(e));
    }
  }

  Future<StateSnapshot> getState() async {
    try {
      final res = await _dio.get('/state');
      return StateSnapshot.fromJson(Map<String, dynamic>.from(res.data as Map));
    } on DioException catch (e) {
      throw ApiException(_extract(e));
    }
  }

  Future<void> triggerKillSwitch({String reason = 'Manual trigger from mobile'}) async {
    try {
      await _dio.post('/kill-switch', data: {'reason': reason});
    } on DioException catch (e) {
      throw ApiException(_extract(e));
    }
  }

  Future<void> clearKillSwitch() async {
    try {
      await _dio.post('/kill-switch/clear');
    } on DioException catch (e) {
      throw ApiException(_extract(e));
    }
  }

  Future<void> patchRiskConfig(Map<String, dynamic> patch) async {
    try {
      await _dio.post('/risk-config', data: patch);
    } on DioException catch (e) {
      throw ApiException(_extract(e));
    }
  }

  static String _extract(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['detail'] is String) return data['detail'] as String;
    return e.message ?? 'Network error';
  }
}
