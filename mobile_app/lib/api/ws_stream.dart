import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

class WsEnvelope {
  WsEnvelope(this.type, this.payload);
  final String type;
  final Map<String, dynamic> payload;
}

/// Auto-reconnecting WebSocket client. Reconnects with exponential backoff
/// (1s, 2s, 4s, ... capped at 30s) whenever the socket drops.
class WsStream {
  WsStream(this.uri);

  final Uri uri;
  final _controller = StreamController<WsEnvelope>.broadcast();
  WebSocketChannel? _channel;
  Timer? _retryTimer;
  int _retryAttempt = 0;
  bool _closed = false;

  Stream<WsEnvelope> get stream => _controller.stream;

  void connect() {
    if (_closed) return;
    try {
      _channel = WebSocketChannel.connect(uri);
      _retryAttempt = 0;
      _channel!.stream.listen(
        _onMessage,
        onError: (_) => _scheduleReconnect(),
        onDone: _scheduleReconnect,
        cancelOnError: true,
      );
    } catch (_) {
      _scheduleReconnect();
    }
  }

  void _onMessage(dynamic raw) {
    if (raw is! String) return;
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      final type = (decoded['type'] ?? 'heartbeat').toString();
      final payload = Map<String, dynamic>.from(decoded['payload'] ?? {});
      _controller.add(WsEnvelope(type, payload));
    } catch (_) {
      // ignore malformed
    }
  }

  void _scheduleReconnect() {
    if (_closed) return;
    _retryAttempt = (_retryAttempt + 1).clamp(1, 5);
    final delay = Duration(seconds: 1 << (_retryAttempt - 1));
    _retryTimer?.cancel();
    _retryTimer = Timer(delay, connect);
  }

  Future<void> close() async {
    _closed = true;
    _retryTimer?.cancel();
    await _channel?.sink.close();
    await _controller.close();
  }
}
