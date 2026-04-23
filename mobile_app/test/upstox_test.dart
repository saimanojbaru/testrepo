import 'package:flutter_test/flutter_test.dart';
import 'package:scalping_agent/brokers/upstox/upstox_auth.dart';
import 'package:scalping_agent/brokers/upstox/upstox_config.dart';

class _MemStore implements TokenStore {
  UpstoxCredentials? _c;
  UpstoxSession? _s;

  @override
  Future<UpstoxCredentials?> loadCredentials() async => _c;
  @override
  Future<void> saveCredentials(UpstoxCredentials c) async => _c = c;
  @override
  Future<UpstoxSession?> loadSession() async => _s;
  @override
  Future<void> saveSession(UpstoxSession s) async => _s = s;
  @override
  Future<void> clearSession() async => _s = null;
  @override
  Future<void> clearAll() async {
    _c = null;
    _s = null;
  }
}

void main() {
  group('UpstoxConfig instrument keys', () {
    test('maps all four indices to NSE/BSE instrument keys', () {
      expect(UpstoxConfig.keyFor('NIFTY'), 'NSE_INDEX|Nifty 50');
      expect(UpstoxConfig.keyFor('BANKNIFTY'), 'NSE_INDEX|Nifty Bank');
      expect(UpstoxConfig.keyFor('FINNIFTY'), 'NSE_INDEX|Nifty Fin Service');
      expect(UpstoxConfig.keyFor('SENSEX'), 'BSE_INDEX|SENSEX');
    });

    test('reverse map recovers our symbol from instrument key', () {
      expect(
          UpstoxConfig.symbolOf['NSE_INDEX|Nifty 50'], 'NIFTY');
      expect(UpstoxConfig.symbolOf['BSE_INDEX|SENSEX'], 'SENSEX');
    });

    test('keyFor returns null for unknown symbol', () {
      expect(UpstoxConfig.keyFor('UNKNOWN'), isNull);
    });
  });

  group('UpstoxCredentials', () {
    test('isComplete requires all three fields', () {
      expect(
          const UpstoxCredentials(
                  apiKey: '', apiSecret: 's', redirectUri: 'r')
              .isComplete,
          isFalse);
      expect(
          const UpstoxCredentials(apiKey: 'k', apiSecret: 's', redirectUri: 'r')
              .isComplete,
          isTrue);
    });

    test('round-trips through toMap / fromMap', () {
      const c = UpstoxCredentials(
          apiKey: 'k', apiSecret: 's', redirectUri: 'https://x/cb');
      final back = UpstoxCredentials.fromMap(c.toMap());
      expect(back.apiKey, 'k');
      expect(back.apiSecret, 's');
      expect(back.redirectUri, 'https://x/cb');
    });
  });

  group('UpstoxSession staleness', () {
    test('fresh session is not stale', () {
      final s = UpstoxSession(accessToken: 't', issuedAt: DateTime.now());
      expect(s.isStale, isFalse);
    });

    test('session older than 18 hours is stale', () {
      final s = UpstoxSession(
          accessToken: 't',
          issuedAt: DateTime.now().subtract(const Duration(hours: 19)));
      expect(s.isStale, isTrue);
    });
  });

  group('UpstoxAuth.buildAuthorizationUrl', () {
    test('encodes client_id + redirect_uri as query params', () {
      final auth = UpstoxAuth();
      final url = auth.buildAuthorizationUrl(
        const UpstoxCredentials(
            apiKey: 'abc',
            apiSecret: 'sec',
            redirectUri: 'https://me.example/cb'),
      );
      expect(url.scheme, 'https');
      expect(url.host, 'api.upstox.com');
      expect(url.path, '/v2/login/authorization/dialog');
      expect(url.queryParameters['client_id'], 'abc');
      expect(url.queryParameters['response_type'], 'code');
      expect(url.queryParameters['redirect_uri'], 'https://me.example/cb');
    });
  });

  group('TokenStore interface', () {
    test('in-memory store round-trips credentials + session', () async {
      final store = _MemStore();
      expect(await store.loadCredentials(), isNull);
      await store.saveCredentials(const UpstoxCredentials(
          apiKey: 'k', apiSecret: 's', redirectUri: 'r'));
      final c = await store.loadCredentials();
      expect(c?.apiKey, 'k');
      final sess = UpstoxSession(
          accessToken: 'tok',
          issuedAt: DateTime(2025, 1, 1));
      await store.saveSession(sess);
      final loaded = await store.loadSession();
      expect(loaded?.accessToken, 'tok');
      await store.clearSession();
      expect(await store.loadSession(), isNull);
      await store.clearAll();
      expect(await store.loadCredentials(), isNull);
    });
  });
}
