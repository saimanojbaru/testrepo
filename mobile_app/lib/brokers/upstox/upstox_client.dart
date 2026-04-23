import 'package:dio/dio.dart';

import 'upstox_config.dart';

class UpstoxQuote {
  const UpstoxQuote({required this.instrumentKey, required this.ltp});
  final String instrumentKey;
  final double ltp;
}

class UpstoxClient {
  UpstoxClient({required this.accessToken, Dio? dio})
      : _dio = dio ??
            Dio(BaseOptions(
              baseUrl: UpstoxConfig.apiBase,
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 10),
            )) {
    _dio.options.headers['Authorization'] = 'Bearer $accessToken';
    _dio.options.headers['Accept'] = 'application/json';
  }

  final String accessToken;
  final Dio _dio;

  /// Fetches last-traded-price for the given instrument keys.
  /// Upstox returns a map keyed by "SEGMENT:TRADING_SYMBOL" — not identical to
  /// the instrument_key passed in. We correlate via the `instrument_token`
  /// field when present, otherwise fall back to substring matching.
  Future<List<UpstoxQuote>> fetchLtp(List<String> instrumentKeys) async {
    if (instrumentKeys.isEmpty) return const [];
    final resp = await _dio.get(
      '/market-quote/ltp',
      queryParameters: {'instrument_key': instrumentKeys.join(',')},
    );
    final data = resp.data;
    if (data is! Map || data['data'] is! Map) return const [];
    final map = (data['data'] as Map).cast<String, dynamic>();
    final out = <UpstoxQuote>[];
    for (final entry in map.entries) {
      final body = entry.value;
      if (body is! Map) continue;
      final ltp = (body['last_price'] as num?)?.toDouble();
      final key = body['instrument_token'] as String? ??
          _guessKey(entry.key, instrumentKeys);
      if (ltp == null || key == null) continue;
      out.add(UpstoxQuote(instrumentKey: key, ltp: ltp));
    }
    return out;
  }

  static String? _guessKey(String responseKey, List<String> requested) {
    // response keys look like "NSE_INDEX:Nifty 50" but requested keys use "|".
    final normalized = responseKey.replaceFirst(':', '|');
    for (final k in requested) {
      if (k == normalized || k.split('|').last == normalized.split('|').last) {
        return k;
      }
    }
    return null;
  }
}
