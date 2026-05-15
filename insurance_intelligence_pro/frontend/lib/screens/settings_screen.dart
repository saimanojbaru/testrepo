import 'package:flutter/material.dart';

import '../features/gamification/services/xp_service.dart';
import '../services/api_service.dart';
import '../theme/app_colors.dart';
import '../widgets/glass_card.dart';
import '../widgets/section_header.dart';

/// Settings screen — for now, scoped to backend URL configuration.
///
/// Users paste their FastAPI deployment URL (e.g. https://iip.fly.dev)
/// and the Updates / Dashboard tabs will overlay live news and SEC
/// EDGAR filings on top of the bundled curated data.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final TextEditingController _ctrl = TextEditingController();
  bool? _reachable;
  bool _testing = false;

  @override
  void initState() {
    super.initState();
    _ctrl.text = ApiService.instance.baseUrl ?? '';
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await ApiService.instance.setBaseUrl(_ctrl.text);
    setState(() => _reachable = null);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: AppColors.surfaceHigh,
        content: Text(
          ApiService.instance.isConfigured
              ? 'Backend URL saved. Live data will overlay curated dataset.'
              : 'Cleared. Running on bundled curated dataset only.',
          style: const TextStyle(color: AppColors.textPrimary),
        ),
      ),
    );
  }

  Future<void> _test() async {
    setState(() => _testing = true);
    await ApiService.instance.setBaseUrl(_ctrl.text);
    final ok = await ApiService.instance.probe();
    if (!mounted) return;
    setState(() {
      _reachable = ok;
      _testing = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: AppColors.background,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text(
          'Settings',
          style: TextStyle(
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w700,
              fontSize: 16),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          const SectionHeader(
              title: 'Fun Mode',
              subtitle: 'Quiz Arena, XP, badges, streaks'),
          GlassCard(
            child: AnimatedBuilder(
              animation: XpService.instance,
              builder: (context, _) {
                final on = XpService.instance.funMode;
                return Row(
                  children: [
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: (on ? AppColors.accent : AppColors.textMuted)
                            .withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                          on
                              ? Icons.celebration_rounded
                              : Icons.work_outline_rounded,
                          color:
                              on ? AppColors.accent : AppColors.textMuted,
                          size: 18),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(on ? 'Fun Mode is ON' : 'Professional Mode',
                              style: const TextStyle(
                                  color: AppColors.textPrimary,
                                  fontWeight: FontWeight.w800,
                                  fontSize: 13)),
                          const SizedBox(height: 2),
                          Text(
                            on
                                ? 'Quiz Arena, Trophy Case, XP, streaks and celebratory animations are visible.'
                                : 'Hide gamification UI. XP & badges are still tracked silently.',
                            style: const TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 11.5,
                                height: 1.4),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: on,
                      activeThumbColor: AppColors.accent,
                      onChanged: (v) =>
                          XpService.instance.setFunMode(v),
                    ),
                  ],
                );
              },
            ),
          ),
          const SizedBox(height: 18),
          const SectionHeader(
              title: 'Real-time backend',
              subtitle: 'Connect your FastAPI deployment to overlay live data'),
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'BACKEND URL',
                  style: TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10,
                      letterSpacing: 1.4,
                      fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 6),
                TextField(
                  controller: _ctrl,
                  keyboardType: TextInputType.url,
                  decoration: const InputDecoration(
                    hintText: 'https://your-iip-backend.example.com',
                    isDense: true,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton(
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          padding:
                              const EdgeInsets.symmetric(vertical: 14),
                        ),
                        onPressed: _save,
                        child: const Text('Save'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.accent,
                          side: const BorderSide(color: AppColors.border),
                          padding:
                              const EdgeInsets.symmetric(vertical: 14),
                        ),
                        onPressed: _testing ? null : _test,
                        child: _testing
                            ? const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: AppColors.accent))
                            : const Text('Test connection'),
                      ),
                    ),
                  ],
                ),
                if (_reachable != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: (_reachable!
                              ? AppColors.positive
                              : AppColors.negative)
                          .withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                          color: (_reachable!
                                  ? AppColors.positive
                                  : AppColors.negative)
                              .withValues(alpha: 0.45)),
                    ),
                    child: Text(
                      _reachable!
                          ? '✓ Reachable — live data will overlay curated dataset.'
                          : '✗ Not reachable. App will use the bundled curated dataset.',
                      style: TextStyle(
                          color: _reachable!
                              ? AppColors.positive
                              : AppColors.negative,
                          fontSize: 12,
                          fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 18),
          const SectionHeader(title: 'How online mode works'),
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                _Bullet(
                    text:
                        'Offline-first: every screen renders instantly from bundled curated data, even with no network.'),
                _Bullet(
                    text:
                        'Online overlay: when configured, the Updates and Dashboard tabs fetch live RSS news, NAIC/SEC bulletins, and live SEC EDGAR filings on background threads.'),
                _Bullet(
                    text:
                        'Failures are silent: if the backend is down, slow, or unreachable, the curated dataset stays visible. No "request timed out" errors.'),
                _Bullet(
                    text:
                        'Strict sourcing: every metric ties back to its filing form (10-K) and accession date, regardless of source.'),
              ],
            ),
          ),
          const SizedBox(height: 18),
          const SectionHeader(title: 'Self-host the backend'),
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  'The FastAPI backend lives in this repo at insurance_intelligence_pro/backend. To deploy:',
                  style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13,
                      height: 1.55),
                ),
                SizedBox(height: 10),
                _Step(
                    n: '1',
                    text:
                        'Render / Fly.io / Railway — Docker container, Python 3.11 base image, expose port 8000.'),
                _Step(
                    n: '2',
                    text:
                        'Install: pip install -r backend/requirements.txt'),
                _Step(
                    n: '3',
                    text:
                        'Run: uvicorn app.main:app --host 0.0.0.0 --port 8000'),
                _Step(
                    n: '4',
                    text:
                        'Configure SEC EDGAR User-Agent: IIP_SEC_USER_AGENT="YourName your@email.com"'),
                _Step(
                    n: '5',
                    text:
                        'Paste the public URL above and tap Save. Live data appears within seconds.'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Bullet extends StatelessWidget {
  final String text;
  const _Bullet({required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 7, right: 8),
            child: SizedBox(
              width: 4,
              height: 4,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: AppColors.accent,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
          Expanded(
            child: Text(text,
                style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                    height: 1.5)),
          ),
        ],
      ),
    );
  }
}

class _Step extends StatelessWidget {
  final String n;
  final String text;
  const _Step({required this.n, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 22,
            height: 22,
            margin: const EdgeInsets.only(right: 10, top: 1),
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(
                  color: AppColors.accent.withValues(alpha: 0.5)),
            ),
            alignment: Alignment.center,
            child: Text(n,
                style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 11,
                    fontWeight: FontWeight.w800)),
          ),
          Expanded(
            child: Text(text,
                style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                    height: 1.5)),
          ),
        ],
      ),
    );
  }
}
