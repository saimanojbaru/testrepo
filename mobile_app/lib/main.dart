import 'package:flex_color_scheme/flex_color_scheme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'fcm/fcm_service.dart';
import 'screens/dashboard.dart';
import 'screens/positions.dart';
import 'screens/reconciliation.dart';
import 'screens/settings.dart';
import 'screens/splash.dart';
import 'screens/strategies.dart';
import 'screens/trade.dart';
import 'screens/trade_feed.dart';
import 'state/broker_controller.dart';
import 'state/market_controller.dart';
import 'update/update_controller.dart';
import 'update/update_sheet.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FcmService.init();
  runApp(const ProviderScope(child: ScalpingAgentApp()));
}

class ScalpingAgentApp extends StatefulWidget {
  const ScalpingAgentApp({super.key});

  @override
  State<ScalpingAgentApp> createState() => _ScalpingAgentAppState();
}

class _ScalpingAgentAppState extends State<ScalpingAgentApp> {
  bool _booted = false;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Scalping Agent',
      debugShowCheckedModeBanner: false,
      theme: FlexThemeData.dark(
        scheme: FlexScheme.deepBlue,
        surfaceMode: FlexSurfaceMode.levelSurfacesLowScaffold,
        blendLevel: 13,
        scaffoldBackground: const Color(0xFF0B1220),
        subThemesData: const FlexSubThemesData(
          blendOnLevel: 20,
          useM2StyleDividerInM3: true,
        ),
        visualDensity: FlexColorScheme.comfortablePlatformDensity,
        useMaterial3: true,
      ),
      home: _booted
          ? const _MainNav()
          : SplashScreen(onContinue: () => setState(() => _booted = true)),
    );
  }
}

class _MainNav extends ConsumerStatefulWidget {
  const _MainNav();

  @override
  ConsumerState<_MainNav> createState() => _MainNavState();
}

class _MainNavState extends ConsumerState<_MainNav> {
  int _idx = 0;

  static const _screens = [
    TradeScreen(),
    StrategiesScreen(),
    ReconciliationScreen(),
    DashboardScreen(),
    PositionsScreen(),
    TradeFeedScreen(),
    SettingsScreen(),
  ];

  bool _didUpdateCheck = false;

  @override
  Widget build(BuildContext context) {
    // Auto-swap to live feed whenever broker becomes (re)connected.
    ref.listen<BrokerController>(brokerControllerProvider, (prev, next) {
      if (next.isConnected && !(prev?.isConnected ?? false)) {
        final client = next.client;
        if (client != null) {
          ref.read(marketControllerProvider).useLiveUpstox(client);
        }
      }
    });

    // Silent update check on first build; prompt if newer APK available.
    ref.listen<UpdateController>(updateControllerProvider, (prev, next) {
      if (next.phase == UpdatePhase.available && next.info != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) UpdateSheet.maybeShow(context, ref);
        });
      }
    });
    if (!_didUpdateCheck) {
      _didUpdateCheck = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(updateControllerProvider).check(silent: true);
      });
    }
    // Fire once on first build in case the bootstrap already completed.
    final bc = ref.read(brokerControllerProvider);
    if (bc.isConnected &&
        !ref.read(marketControllerProvider).isLiveFeed &&
        bc.client != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(marketControllerProvider).useLiveUpstox(bc.client!);
      });
    }
    return Scaffold(
      body: IndexedStack(index: _idx, children: _screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _idx,
        onDestinationSelected: (i) => setState(() => _idx = i),
        destinations: const [
          NavigationDestination(
              icon: Icon(Icons.candlestick_chart), label: 'Trade'),
          NavigationDestination(
              icon: Icon(Icons.auto_graph), label: 'Strategies'),
          NavigationDestination(
              icon: Icon(Icons.receipt_long), label: 'Audit'),
          NavigationDestination(
              icon: Icon(Icons.dashboard), label: 'Dashboard'),
          NavigationDestination(
              icon: Icon(Icons.account_balance_wallet), label: 'Positions'),
          NavigationDestination(icon: Icon(Icons.timeline), label: 'Feed'),
          NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}
