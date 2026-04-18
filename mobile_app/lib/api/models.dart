class RiskStatus {
  RiskStatus({
    required this.dailyPnl,
    required this.tradesToday,
    required this.openPositions,
    required this.halted,
    required this.haltReason,
    required this.killSwitchActive,
    required this.dailyLossCap,
    required this.tradingCapital,
  });

  final double dailyPnl;
  final int tradesToday;
  final int openPositions;
  final bool halted;
  final String haltReason;
  final bool killSwitchActive;
  final double dailyLossCap;
  final double tradingCapital;

  factory RiskStatus.fromJson(Map<String, dynamic> j) => RiskStatus(
        dailyPnl: (j['daily_pnl'] ?? 0).toDouble(),
        tradesToday: (j['trades_today'] ?? 0) as int,
        openPositions: (j['open_positions'] ?? 0) as int,
        halted: j['halted'] ?? false,
        haltReason: (j['halt_reason'] ?? '').toString(),
        killSwitchActive: j['kill_switch_active'] ?? false,
        dailyLossCap: (j['daily_loss_cap'] ?? 2000).toDouble(),
        tradingCapital: (j['trading_capital'] ?? 100000).toDouble(),
      );

  static RiskStatus empty() => RiskStatus(
        dailyPnl: 0,
        tradesToday: 0,
        openPositions: 0,
        halted: false,
        haltReason: '',
        killSwitchActive: false,
        dailyLossCap: 2000,
        tradingCapital: 100000,
      );
}

class PositionDto {
  PositionDto({
    required this.symbol,
    required this.quantity,
    required this.averagePrice,
    required this.lastPrice,
    required this.unrealizedPnl,
  });

  final String symbol;
  final int quantity;
  final double averagePrice;
  final double lastPrice;
  final double unrealizedPnl;

  factory PositionDto.fromJson(Map<String, dynamic> j) => PositionDto(
        symbol: (j['symbol'] ?? '').toString(),
        quantity: (j['quantity'] ?? 0) as int,
        averagePrice: (j['average_price'] ?? 0).toDouble(),
        lastPrice: (j['last_price'] ?? 0).toDouble(),
        unrealizedPnl: (j['unrealized_pnl'] ?? 0).toDouble(),
      );
}

class TradeEvent {
  TradeEvent({
    required this.timestamp,
    required this.kind,
    required this.message,
    this.symbol,
    this.side,
    this.quantity,
    this.price,
    this.pnl,
    this.strategy,
  });

  final DateTime timestamp;
  final String kind;
  final String message;
  final String? symbol;
  final String? side;
  final int? quantity;
  final double? price;
  final double? pnl;
  final String? strategy;

  factory TradeEvent.fromJson(Map<String, dynamic> j) => TradeEvent(
        timestamp: DateTime.tryParse(j['timestamp']?.toString() ?? '') ?? DateTime.now(),
        kind: (j['kind'] ?? 'heartbeat').toString(),
        message: (j['message'] ?? '').toString(),
        symbol: j['symbol']?.toString(),
        side: j['side']?.toString(),
        quantity: j['quantity'] as int?,
        price: (j['price'] as num?)?.toDouble(),
        pnl: (j['pnl'] as num?)?.toDouble(),
        strategy: j['strategy']?.toString(),
      );
}

class StateSnapshot {
  StateSnapshot({
    required this.paperMode,
    required this.symbol,
    required this.risk,
    required this.positions,
    required this.events,
    required this.pnlCurve,
  });

  final bool paperMode;
  final String symbol;
  final RiskStatus risk;
  final List<PositionDto> positions;
  final List<TradeEvent> events;
  final List<double> pnlCurve;

  factory StateSnapshot.fromJson(Map<String, dynamic> j) => StateSnapshot(
        paperMode: j['paper_mode'] ?? true,
        symbol: (j['symbol'] ?? 'NIFTY').toString(),
        risk: RiskStatus.fromJson(Map<String, dynamic>.from(j['risk'] ?? {})),
        positions: ((j['positions'] as List?) ?? [])
            .map((p) => PositionDto.fromJson(Map<String, dynamic>.from(p as Map)))
            .toList(),
        events: ((j['recent_events'] as List?) ?? [])
            .map((e) => TradeEvent.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        pnlCurve: ((j['pnl_curve'] as List?) ?? [])
            .map((v) => (v as num).toDouble())
            .toList(),
      );

  static StateSnapshot empty() => StateSnapshot(
        paperMode: true,
        symbol: 'NIFTY',
        risk: RiskStatus.empty(),
        positions: const [],
        events: const [],
        pnlCurve: const [],
      );
}
