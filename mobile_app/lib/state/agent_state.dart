import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/client.dart';
import '../api/ws_stream.dart';

class AgentState {
  final double capital;
  final double dailyPnl;
  final double weeklyPnl;
  final double realizedPnl;
  final double unrealizedPnl;
  final int openPositions;
  final int tradesToday;
  final bool killSwitchEngaged;
  final String? regime;
  final List<Map<String, dynamic>> positions;
  final List<Map<String, dynamic>> tradeEvents;
  final bool isConnected;

  const AgentState({
    this.capital = 0,
    this.dailyPnl = 0,
    this.weeklyPnl = 0,
    this.realizedPnl = 0,
    this.unrealizedPnl = 0,
    this.openPositions = 0,
    this.tradesToday = 0,
    this.killSwitchEngaged = false,
    this.regime,
    this.positions = const [],
    this.tradeEvents = const [],
    this.isConnected = false,
  });

  AgentState copyWith({
    double? capital,
    double? dailyPnl,
    double? weeklyPnl,
    double? realizedPnl,
    double? unrealizedPnl,
    int? openPositions,
    int? tradesToday,
    bool? killSwitchEngaged,
    String? regime,
    List<Map<String, dynamic>>? positions,
    List<Map<String, dynamic>>? tradeEvents,
    bool? isConnected,
  }) =>
      AgentState(
        capital: capital ?? this.capital,
        dailyPnl: dailyPnl ?? this.dailyPnl,
        weeklyPnl: weeklyPnl ?? this.weeklyPnl,
        realizedPnl: realizedPnl ?? this.realizedPnl,
        unrealizedPnl: unrealizedPnl ?? this.unrealizedPnl,
        openPositions: openPositions ?? this.openPositions,
        tradesToday: tradesToday ?? this.tradesToday,
        killSwitchEngaged: killSwitchEngaged ?? this.killSwitchEngaged,
        regime: regime ?? this.regime,
        positions: positions ?? this.positions,
        tradeEvents: tradeEvents ?? this.tradeEvents,
        isConnected: isConnected ?? this.isConnected,
      );

  factory AgentState.fromSnapshot(Map<String, dynamic> j) => AgentState(
        capital: (j['capital'] as num?)?.toDouble() ?? 0,
        dailyPnl: (j['daily_pnl'] as num?)?.toDouble() ?? 0,
        weeklyPnl: (j['weekly_pnl'] as num?)?.toDouble() ?? 0,
        realizedPnl: (j['realized_pnl'] as num?)?.toDouble() ?? 0,
        unrealizedPnl: (j['unrealized_pnl'] as num?)?.toDouble() ?? 0,
        openPositions: (j['open_positions'] as int?) ?? 0,
        tradesToday: (j['trades_today'] as int?) ?? 0,
        killSwitchEngaged: (j['kill_switch_engaged'] as bool?) ?? false,
        regime: j['regime'] as String?,
        positions: (j['positions'] as List<dynamic>?)
                ?.cast<Map<String, dynamic>>() ??
            [],
        isConnected: true,
      );
}

class AgentStateNotifier extends StateNotifier<AgentState> {
  AgentStateNotifier() : super(const AgentState());

  WsStream? _ws;

  Future<void> connect(String backendUrl, String token) async {
    // Fetch initial snapshot
    try {
      final snap = await ApiClient.instance.getState();
      state = AgentState.fromSnapshot(snap);
    } catch (_) {}

    _ws = WsStream(backendUrl, token);
    _ws!.stream.listen(_onEvent);
    _ws!.connect();
  }

  void _onEvent(AgentEvent event) {
    switch (event.kind) {
      case AgentEventKind.snapshot:
        state = AgentState.fromSnapshot(event.data).copyWith(isConnected: true);
      case AgentEventKind.delta:
        _applyDelta(event.deltaKind ?? '', event.data);
      case AgentEventKind.heartbeat:
        state = state.copyWith(isConnected: true);
      case AgentEventKind.unknown:
        break;
    }
  }

  void _applyDelta(String kind, Map<String, dynamic> data) {
    switch (kind) {
      case 'bar':
        state = state.copyWith(
          realizedPnl: (data['realized_pnl'] as num?)?.toDouble() ?? state.realizedPnl,
          unrealizedPnl: (data['unrealized_pnl'] as num?)?.toDouble() ?? state.unrealizedPnl,
        );
      case 'kill_switch':
        state = state.copyWith(
          killSwitchEngaged: (data['engaged'] as bool?) ?? state.killSwitchEngaged,
        );
      case 'fill':
      case 'close':
      case 'signal':
        final events = [data, ...state.tradeEvents].take(100).toList();
        state = state.copyWith(
          tradeEvents: events.cast<Map<String, dynamic>>(),
        );
      default:
        break;
    }
  }

  @override
  void dispose() {
    _ws?.dispose();
    super.dispose();
  }
}

final agentStateProvider =
    StateNotifierProvider<AgentStateNotifier, AgentState>(
  (_) => AgentStateNotifier(),
);
