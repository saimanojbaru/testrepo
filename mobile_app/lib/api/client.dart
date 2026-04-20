import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _tokenKey = 'jwt_token';
const _baseUrlKey = 'backend_url';

class ApiClient {
  ApiClient._();

  static final ApiClient instance = ApiClient._();

  late final Dio _dio;
  bool _initialized = false;

  Future<void> init(String baseUrl) async {
    _dio = Dio(BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 8),
    ));
    _initialized = true;
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_tokenKey);
    if (token != null) _setToken(token);
  }

  void _setToken(String token) {
    _dio.options.headers['Authorization'] = 'Bearer $token';
  }

  Future<String> login(String backendUrl, String secret) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_baseUrlKey, backendUrl);
    await init(backendUrl);

    final resp = await _dio.post<Map<String, dynamic>>('/login', data: {'secret': secret});
    final token = resp.data!['token'] as String;
    await prefs.setString(_tokenKey, token);
    _setToken(token);
    return token;
  }

  Future<Map<String, dynamic>> getState() async {
    final resp = await _dio.get<Map<String, dynamic>>('/state');
    return resp.data!;
  }

  Future<void> triggerKillSwitch({required bool engage, String reason = 'manual'}) async {
    await _dio.post<void>('/kill-switch', data: {'engage': engage, 'reason': reason});
  }

  Future<void> patchRiskConfig(Map<String, dynamic> patch) async {
    await _dio.post<void>('/risk-config', data: patch);
  }

  /// Returns the stored backend URL, or null if not yet configured.
  static Future<String?> savedBackendUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_baseUrlKey);
  }
}
