import 'package:flex_color_scheme/flex_color_scheme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'fcm/fcm_service.dart';
import 'screens/dashboard.dart';
import 'screens/positions.dart';
import 'screens/settings.dart';
import 'screens/trade_feed.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FcmService.init();
  runApp(const ProviderScope(child: ScalpingAgentApp()));
}

class ScalpingAgentApp extends StatelessWidget {
  const ScalpingAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Scalping Agent',
      theme: FlexThemeData.dark(
        scheme: FlexScheme.deepBlue,
        surfaceMode: FlexSurfaceMode.levelSurfacesLowScaffold,
        blendLevel: 13,
        subThemesData: const FlexSubThemesData(
          blendOnLevel: 20,
          useM2StyleDividerInM3: true,
        ),
        visualDensity: FlexColorScheme.comfortablePlatformDensity,
        useMaterial3: true,
      ),
      home: const _MainNav(),
    );
  }
}

class _MainNav extends StatefulWidget {
  const _MainNav();

  @override
  State<_MainNav> createState() => _MainNavState();
}

class _MainNavState extends State<_MainNav> {
  int _idx = 0;

  static const _screens = [
    DashboardScreen(),
    PositionsScreen(),
    TradeFeedScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _screens[_idx],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _idx,
        onDestinationSelected: (i) => setState(() => _idx = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.account_balance_wallet), label: 'Positions'),
          NavigationDestination(icon: Icon(Icons.timeline), label: 'Feed'),
          NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}
