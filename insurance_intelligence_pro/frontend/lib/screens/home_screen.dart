import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../theme/app_colors.dart';
import 'company_screen.dart';
import 'compare_screen.dart';
import 'dashboard_screen.dart';
import 'knowledge_screen.dart';
import 'updates_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  final List<Widget> _pages = const [
    DashboardScreen(),
    CompanyScreen(),
    CompareScreen(),
    UpdatesScreen(),
    KnowledgeScreen(),
  ];

  static const _tabs = <_TabSpec>[
    _TabSpec(label: 'Dashboard', icon: Icons.insights_rounded),
    _TabSpec(label: 'Company', icon: Icons.business_center_rounded),
    _TabSpec(label: 'Compare', icon: Icons.compare_arrows_rounded),
    _TabSpec(label: 'Updates', icon: Icons.bolt_rounded),
    _TabSpec(label: 'Knowledge', icon: Icons.menu_book_rounded),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      extendBody: true,
      body: Stack(
        children: [
          const _AmbientBackground(),
          IndexedStack(index: _index, children: _pages),
        ],
      ),
      bottomNavigationBar: _NavBar(
        index: _index,
        tabs: _tabs,
        onChanged: (i) => setState(() => _index = i),
      ),
    );
  }
}

class _TabSpec {
  final String label;
  final IconData icon;
  const _TabSpec({required this.label, required this.icon});
}

class _NavBar extends StatelessWidget {
  final int index;
  final List<_TabSpec> tabs;
  final ValueChanged<int> onChanged;
  const _NavBar({
    required this.index,
    required this.tabs,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        decoration: BoxDecoration(
          color: AppColors.surface.withOpacity(0.85),
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: AppColors.border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.4),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: Row(
          children: List.generate(tabs.length, (i) {
            final selected = i == index;
            return Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 240),
                  curve: Curves.easeOutCubic,
                  margin: const EdgeInsets.symmetric(horizontal: 2),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                  decoration: BoxDecoration(
                    gradient: selected ? AppColors.primaryGradient : null,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        tabs[i].icon,
                        color: selected ? Colors.white : AppColors.textMuted,
                        size: 20,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        tabs[i].label,
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w600,
                          color: selected ? Colors.white : AppColors.textMuted,
                          letterSpacing: 0.3,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

class _AmbientBackground extends StatelessWidget {
  const _AmbientBackground();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Stack(
        children: [
          Positioned(
            top: -120,
            right: -90,
            child: Container(
              width: 280,
              height: 280,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppColors.primary.withOpacity(0.35),
                    Colors.transparent
                  ],
                ),
              ),
            ).animate(onPlay: (c) => c.repeat(reverse: true))
                .fadeIn(duration: 4000.ms),
          ),
          Positioned(
            bottom: -160,
            left: -120,
            child: Container(
              width: 320,
              height: 320,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppColors.accent.withOpacity(0.18),
                    Colors.transparent
                  ],
                ),
              ),
            ).animate(onPlay: (c) => c.repeat(reverse: true))
                .fadeIn(duration: 5000.ms),
          ),
        ],
      ),
    );
  }
}
