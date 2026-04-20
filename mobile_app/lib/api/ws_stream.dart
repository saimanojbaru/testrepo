import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

enum AgentEventKind {
  snapshot,
  delta,
  heartbeat,
  unknown,
}

class AgentEvent {
  final AgentEventKind kind;
  final Map<String, dynamic> data;
  final String? deltaKind;

  const AgentEvent({required this.kind, required this.data, this.deltaKind});

  factory AgentEvent.fromJson(Map<String, dynamic> j) {
    final type = j['type'] as String? ?? 'unknown';
    return AgentEvent(
      kind: switch (type) {
        'snapshot' => AgentEventKind.snapshot,
        'delta' => AgentEventKind.delta,
        'heartbeat' => AgentEventKind.heartbeat,
        _ => AgentEventKind.unknown,
      },
      data: (j['data'] as Map<String, dynamic>?) ?? {},
      deltaKind: j['kind'] as String?,
    );
  }
}

class WsStream {
  WsStream(this._baseUrl, this._token);

  final String _baseUrl;
  final String _token;

  WebSocketChannel? _channel;
  final _controller = StreamController<AgentEvent>.broadcast();
  bool _running = false;

  Stream<AgentEvent> get stream => _controller.stream;

  void connect() {
    if (_running) return;
    _running = true;
    _reconnect();
  }

  void _reconnect() async {
    if (!_running) return;
    final wsUrl = _baseUrl
        .replaceFirst('http://', 'ws://')
        .replaceFirst('https://', 'wss://');
    final uri = Uri.parse('$wsUrl/ws/stream?token=$_token');
    try {
      _channel = WebSocketChannel.connect(uri);
      await for (final raw in _channel!.stream) {
        final json = jsonDecode(raw as String) as Map<String, dynamic>;
        _controller.add(AgentEvent.fromJson(json));
      }
    } catch (_) {
      // ignored — reconnect below
    }
    if (_running) {
      await Future<void>.delayed(const Duration(seconds: 3));
      _reconnect();
    }
  }

  void dispose() {
    _running = false;
    _channel?.sink.close();
    _controller.close();
  }
}
