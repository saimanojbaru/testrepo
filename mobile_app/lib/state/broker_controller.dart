import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../brokers/upstox/upstox_auth.dart';
import '../brokers/upstox/upstox_client.dart';

enum BrokerStatus { disconnected, connecting, connected, error }

class BrokerController extends ChangeNotifier {
  BrokerController({TokenStore? store, UpstoxAuth? auth})
      : _store = store ?? SecureTokenStore(),
        _auth = auth ?? UpstoxAuth() {
    _bootstrap();
  }

  final TokenStore _store;
  final UpstoxAuth _auth;

  BrokerStatus status = BrokerStatus.disconnected;
  UpstoxCredentials? credentials;
  UpstoxSession? session;
  String? lastError;

  bool get isConnected =>
      status == BrokerStatus.connected && session != null && !session!.isStale;

  UpstoxClient? get client {
    final s = session;
    if (s == null || s.isStale) return null;
    return UpstoxClient(accessToken: s.accessToken);
  }

  Future<void> _bootstrap() async {
    try {
      credentials = await _store.loadCredentials();
      session = await _store.loadSession();
      if (session != null && !session!.isStale) {
        status = BrokerStatus.connected;
      } else if (session != null && session!.isStale) {
        status = BrokerStatus.error;
        lastError = 'Token expired — reconnect required.';
      }
    } catch (e) {
      lastError = e.toString();
      status = BrokerStatus.error;
    }
    notifyListeners();
  }

  Future<void> saveCredentials(UpstoxCredentials c) async {
    credentials = c;
    await _store.saveCredentials(c);
    notifyListeners();
  }

  Uri authorizationUrl() {
    final c = credentials;
    if (c == null || !c.isComplete) {
      throw StateError('Credentials not set');
    }
    return _auth.buildAuthorizationUrl(c);
  }

  Future<void> exchangeCode(String code) async {
    final c = credentials;
    if (c == null) {
      throw StateError('Credentials not set');
    }
    status = BrokerStatus.connecting;
    lastError = null;
    notifyListeners();
    try {
      final s = await _auth.exchange(credentials: c, code: code);
      session = s;
      await _store.saveSession(s);
      status = BrokerStatus.connected;
    } catch (e) {
      status = BrokerStatus.error;
      lastError = e.toString();
      rethrow;
    } finally {
      notifyListeners();
    }
  }

  Future<void> disconnect() async {
    await _store.clearSession();
    session = null;
    status = BrokerStatus.disconnected;
    lastError = null;
    notifyListeners();
  }

  Future<void> forgetEverything() async {
    await _store.clearAll();
    credentials = null;
    session = null;
    status = BrokerStatus.disconnected;
    lastError = null;
    notifyListeners();
  }
}

final brokerControllerProvider =
    ChangeNotifierProvider<BrokerController>((ref) {
  final ctrl = BrokerController();
  ref.onDispose(ctrl.dispose);
  return ctrl;
});
