import 'package:flutter_test/flutter_test.dart';

// Basic smoke test — verifies the app compiles and
// AgentState default values are sane. No network calls.
import 'package:scalping_agent/state/agent_state.dart';

void main() {
  test('AgentState defaults are zeroed', () {
    const s = AgentState();
    expect(s.dailyPnl, equals(0.0));
    expect(s.killSwitchEngaged, isFalse);
    expect(s.positions, isEmpty);
  });

  test('AgentState.fromSnapshot parses correctly', () {
    final s = AgentState.fromSnapshot({
      'capital': 100000.0,
      'daily_pnl': -500.0,
      'kill_switch_engaged': true,
      'positions': [],
    });
    expect(s.capital, equals(100000.0));
    expect(s.dailyPnl, equals(-500.0));
    expect(s.killSwitchEngaged, isTrue);
  });

  test('AgentState.copyWith preserves unchanged fields', () {
    const original = AgentState(tradesToday: 5, capital: 50000);
    final updated = original.copyWith(dailyPnl: 200.0);
    expect(updated.tradesToday, equals(5));
    expect(updated.capital, equals(50000.0));
    expect(updated.dailyPnl, equals(200.0));
  });
}
