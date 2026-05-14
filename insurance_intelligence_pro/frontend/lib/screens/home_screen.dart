import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../theme/app_colors.dart';
import 'company_screen.dart';
import 'compare_screen.dart';
import 'dashboard_screen.dart';
import 'knowledge_screen.dart';
import 'products_index_screen.dart';
import 'settings_screen.dart';
import 'standards_index_screen.dart';
import 'updates_screen.dart';

/// Top-level scaffold using a left Drawer with nested expansion for
/// the Knowledge hierarchy. Replaces the previous bottom-nav layout.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  _Tab _selected = _Tab.dashboard;

  String _title() {
    switch (_selected) {
      case _Tab.dashboard:
        return 'Dashboard';
      case _Tab.company:
        return 'Company';
      case _Tab.compare:
        return 'Compare';
      case _Tab.updates:
        return 'Industry Updates';
      case _Tab.knowledge:
        return 'Knowledge';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      drawer: _AppDrawer(
        selected: _selected,
        onTabSelected: (t) {
          setState(() => _selected = t);
          Navigator.of(context).pop();
        },
        onStandardsSelected: (framework) {
          Navigator.of(context).pop();
          Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => StandardsIndexScreen(framework: framework)));
        },
        onProductLibrary: () {
          Navigator.of(context).pop();
          Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => const ProductsIndexScreen()));
        },
        onSettings: () {
          Navigator.of(context).pop();
          Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => const SettingsScreen()));
        },
      ),
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text(
          _title(),
          style: const TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w800,
              fontSize: 18),
        ),
      ),
      body: Stack(
        children: [
          const _AmbientBackground(),
          IndexedStack(
            index: _selected.index,
            children: const [
              DashboardScreen(),
              CompanyScreen(),
              CompareScreen(),
              UpdatesScreen(),
              KnowledgeScreen(),
            ],
          ),
        ],
      ),
    );
  }
}

enum _Tab { dashboard, company, compare, updates, knowledge }

class _AppDrawer extends StatelessWidget {
  final _Tab selected;
  final ValueChanged<_Tab> onTabSelected;
  final ValueChanged<String> onStandardsSelected;
  final VoidCallback onProductLibrary;
  final VoidCallback onSettings;
  const _AppDrawer({
    required this.selected,
    required this.onTabSelected,
    required this.onStandardsSelected,
    required this.onProductLibrary,
    required this.onSettings,
  });

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.horizontal(right: Radius.circular(24)),
      ),
      child: SafeArea(
        child: ListView(
          padding: EdgeInsets.zero,
          children: [
            const _DrawerHeader(),
            const Divider(color: AppColors.divider, height: 1),
            const SizedBox(height: 6),
            _DrawerTile(
              icon: Icons.insights_rounded,
              label: 'Dashboard',
              selected: selected == _Tab.dashboard,
              onTap: () => onTabSelected(_Tab.dashboard),
            ),
            _DrawerTile(
              icon: Icons.business_center_rounded,
              label: 'Company',
              selected: selected == _Tab.company,
              onTap: () => onTabSelected(_Tab.company),
            ),
            _DrawerTile(
              icon: Icons.compare_arrows_rounded,
              label: 'Compare',
              selected: selected == _Tab.compare,
              onTap: () => onTabSelected(_Tab.compare),
            ),
            _DrawerTile(
              icon: Icons.bolt_rounded,
              label: 'Updates',
              selected: selected == _Tab.updates,
              onTap: () => onTabSelected(_Tab.updates),
            ),
            const SizedBox(height: 4),
            const Divider(color: AppColors.divider, height: 1),
            // Knowledge expansion — required by spec
            Theme(
              data: Theme.of(context).copyWith(
                dividerColor: Colors.transparent,
                splashColor: AppColors.accent.withValues(alpha: 0.06),
              ),
              child: ExpansionTile(
                tilePadding:
                    const EdgeInsets.symmetric(horizontal: 18, vertical: 4),
                childrenPadding:
                    const EdgeInsets.fromLTRB(18, 0, 18, 8),
                initiallyExpanded: true,
                iconColor: AppColors.accent,
                collapsedIconColor: AppColors.textMuted,
                leading: Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: const Icon(Icons.menu_book_rounded,
                      color: AppColors.accent, size: 18),
                ),
                title: const Text('Knowledge',
                    style: TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        letterSpacing: 0.4)),
                subtitle: const Padding(
                  padding: EdgeInsets.only(top: 2),
                  child: Text(
                    'FSLI library + standards index',
                    style: TextStyle(
                        color: AppColors.textMuted, fontSize: 11),
                  ),
                ),
                children: [
                  _SubTile(
                    label: 'Product Library',
                    helper: '16 products · Life · Annuity · Institutional · P&C',
                    onTap: onProductLibrary,
                  ),
                  _SubTile(
                    label: 'Knowledge Library',
                    helper: '37 FSLI articles',
                    onTap: () => onTabSelected(_Tab.knowledge),
                    selected: selected == _Tab.knowledge,
                  ),
                  _SubTile(
                    label: 'SSAP',
                    helper: '96 chapters · 2026 NAIC AP&P (1 → 122)',
                    onTap: () => onStandardsSelected('SSAP'),
                  ),
                  _SubTile(
                    label: 'ASC 944',
                    helper: '27 sub-topics · FASB Codification',
                    onTap: () => onStandardsSelected('ASC944'),
                  ),
                  _SubTile(
                    label: 'PCAOB Guidelines',
                    helper: '24 auditing standards',
                    onTap: () => onStandardsSelected('PCAOB'),
                  ),
                ],
              ),
            ),
            const Divider(color: AppColors.divider, height: 1),
            const SizedBox(height: 4),
            _DrawerTile(
              icon: Icons.settings_outlined,
              label: 'Settings',
              selected: false,
              onTap: onSettings,
            ),
            const SizedBox(height: 16),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: Text(
                'Insurance Intelligence Pro · v1.0',
                style:
                    TextStyle(color: AppColors.textMuted, fontSize: 10),
              ),
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}

class _DrawerHeader extends StatelessWidget {
  const _DrawerHeader();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.bolt_rounded,
                color: Colors.white, size: 18),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Insurance Intelligence',
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                    letterSpacing: -0.4,
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  'Pro · v1.0',
                  style: TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10.5,
                      letterSpacing: 1.4,
                      fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms);
  }
}

class _DrawerTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _DrawerTile({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
            decoration: BoxDecoration(
              gradient: selected ? AppColors.primaryGradient : null,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: selected
                        ? Colors.white.withValues(alpha: 0.15)
                        : AppColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                        color: selected
                            ? Colors.white.withValues(alpha: 0.4)
                            : AppColors.border),
                  ),
                  child: Icon(icon,
                      color: selected ? Colors.white : AppColors.accent,
                      size: 18),
                ),
                const SizedBox(width: 12),
                Text(label,
                    style: TextStyle(
                        color: selected
                            ? Colors.white
                            : AppColors.textPrimary,
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        letterSpacing: 0.3)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SubTile extends StatelessWidget {
  final String label;
  final String helper;
  final VoidCallback onTap;
  final bool selected;
  const _SubTile({
    required this.label,
    required this.helper,
    required this.onTap,
    this.selected = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.symmetric(
                horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: selected
                  ? AppColors.accent.withValues(alpha: 0.12)
                  : AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: selected
                      ? AppColors.accent.withValues(alpha: 0.5)
                      : AppColors.border),
            ),
            child: Row(
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    color: selected
                        ? AppColors.accent
                        : AppColors.textMuted,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(label,
                          style: TextStyle(
                              color: selected
                                  ? AppColors.accent
                                  : AppColors.textPrimary,
                              fontWeight: FontWeight.w700,
                              fontSize: 13)),
                      const SizedBox(height: 2),
                      Text(helper,
                          style: const TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 10.5)),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded,
                    color: AppColors.textMuted, size: 16),
              ],
            ),
          ),
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
                    AppColors.primary.withValues(alpha: 0.35),
                    Colors.transparent,
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
                    AppColors.accent.withValues(alpha: 0.18),
                    Colors.transparent,
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
