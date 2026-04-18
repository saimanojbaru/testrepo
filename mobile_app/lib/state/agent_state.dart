import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/client.dart';
import '../api/models.dart';
import '../api/ws_stream.dart';
import 'auth.dart';

class AgentState {
  AgentState({
    required this.snapshot,
    this.connected = false,
    this.error,
  });

  final StateSnapshot snapshot;
  final bool connected;
  final String? error;

  AgentState copyWith({StateSnapshot? snapshot, bool? connected, String? error}) =>
      AgentState(
        snapshot: snapshot ?? this.snapshot,
        connected: connected ?? this.connected,
        error: error,
      );
}

class AgentController extends StateNotifier<AgentState> {
  AgentController(this._client) : super(AgentState(snapshot: StateSnapshot.empty())) {
    _start();
  }

  final ApiClient _client;
  WsStream? _ws;
  Timer? _refreshTimer;

  Future<void> _start() async {
    await _fetchSnapshot();
    _ws = WsStream(_client.wsUri('/ws/stream'));
    _ws!.stream.listen(_onWsEvent);
    _ws!.connect();
    _refreshTimer = Timer.periodic(const Duration(seconds: 30), (_) => _fetchSnapshot());
  }

  Future<void> _fetchSnapshot() async {
    try {
      final snap = await _client.getState();
      state = state.copyWith(snapshot: snap, connected: true, error: null);
    } on ApiException catch (e) {
      state = state.copyWith(error: e.message, connected: false);
    }
  }

  void _onWsEvent(WsEnvelope env) {
    switch (env.type) {
      case 'snapshot':
        state = state.copyWith(
          snapshot: StateSnapshot.fromJson(env.payload),
          connected: true,
          error: null,
        );
        break;
      case 'risk':
        final newRisk = RiskStatus.fromJson(env.payload);
        final curve = List<double>.from(state.snapshot.pnlCurve)..add(newRisk.dailyPnl);
        if (curve.length > 300) curve.removeRange(0, curve.length - 300);
        state = state.copyWith(
          snapshot: StateSnapshot(
            paperMode: state.snapshot.paperMode,
            symbol: state.snapshot.symbol,
            risk: newRisk,
            positions: state.snapshot.positions,
            events: state.snapshot.events,
            pnlCurve: curve,
          ),
          connected: true,
        );
        break;
      case 'event':
        final ev = TradeEvent.fromJson(env.payload);
        final events = [ev, ...state.snapshot.events];
        if (events.length > 200) events.removeRange(200, events.length);
        state = state.copyWith(
          snapshot: StateSnapshot(
            paperMode: state.snapshot.paperMode,
            symbol: state.snapshot.symbol,
            risk: state.snapshot.risk,
            positions: state.snapshot.positions,
            events: events,
            pnlCurve: state.snapshot.pnlCurve,
          ),
          connected: true,
        );
        break;
      case 'heartbeat':
        state = state.copyWith(connected: true);
        break;
    }
  }

  Future<void> triggerKillSwitch() => _client.triggerKillSwitch();
  Future<void> clearKillSwitch() => _client.clearKillSwitch();
  Future<void> patchRiskConfig(Map<String, dynamic> patch) => _client.patchRiskConfig(patch);

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _ws?.close();
    super.dispose();
  }
}

final apiClientProvider = Provider<ApiClient>((ref) {
  final auth = ref.watch(authProvider);
  if (auth.baseUrl == null || auth.token == null) {
    throw StateError('Not authenticated');
  }
  return ApiClient(auth.baseUrl!, token: auth.token);
});

final agentProvider = StateNotifierProvider<AgentController, AgentState>((ref) {
  final client = ref.watch(apiClientProvider);
  return AgentController(client);
});
