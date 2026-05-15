import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/insight.dart';
import '../services/analytics_service.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import '../widgets/glass_card.dart';
import '../services/api_service.dart';
import '../services/live_news_service.dart';
import '../widgets/insight_card.dart';
import '../widgets/section_header.dart';
import '../widgets/sparkline.dart';
import 'news_detail_screen.dart';
import 'settings_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  List<NewsItem> _liveHeadlines = const [];

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    final live = await LiveNewsService.instance.fetchAll();
    if (!mounted) return;
    setState(() => _liveHeadlines = live.take(6).toList());
  }

  @override
  Widget build(BuildContext context) {
    final pulse = AnalyticsService.instance.pulse();
    final mp = MarketPulse.fromJson(pulse);
    // Live headlines first; curated baseline appended for resilience.
    final curated = AnalyticsService.instance
        .topNews(limit: 5)
        .map((e) => NewsItem.fromJson(e))
        .toList();
    final seen = <String>{};
    final headlines = <NewsItem>[];
    for (final n in [..._liveHeadlines, ...curated]) {
      final k = n.title.trim().toLowerCase();
      if (seen.contains(k)) continue;
      seen.add(k);
      headlines.add(n);
      if (headlines.length >= 6) break;
    }
    final isLive = _liveHeadlines.isNotEmpty;

    return DefaultTabController(
      length: 4,
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
              child: _DashboardHeader(isLive: isLive),
            ),
            // Glow-edged top tabs
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 20),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated.withValues(alpha: 0.6),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.border),
              ),
              child: TabBar(
                isScrollable: false,
                dividerColor: Colors.transparent,
                indicator: BoxDecoration(
                  gradient: AppColors.primaryGradient,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow:
                      AppColors.glow(AppColors.accent, radius: 14),
                ),
                indicatorPadding: const EdgeInsets.all(4),
                labelPadding: EdgeInsets.zero,
                labelColor: Colors.white,
                unselectedLabelColor: AppColors.textMuted,
                labelStyle: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 11.5,
                    letterSpacing: 0.4),
                tabs: const [
                  Tab(text: 'Today'),
                  Tab(text: 'My Clients'),
                  Tab(text: 'Market Pulse'),
                  Tab(text: 'Risk Watch'),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: RefreshIndicator(
                onRefresh: _refresh,
                color: AppColors.accent,
                backgroundColor: AppColors.surface,
                child: TabBarView(
                  children: [
                    _TodayTab(
                        mp: mp,
                        headlines: headlines,
                        isLive: isLive,
                        showInsightDetail: _showInsightDetail),
                    const _MyClientsTab(),
                    _MarketPulseTab(mp: mp),
                    const _RiskWatchTab(),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Tab 1: Today — daily take + insights + top headlines ──────────────────

class _TodayTab extends StatelessWidget {
  final MarketPulse mp;
  final List<NewsItem> headlines;
  final bool isLive;
  final void Function(BuildContext, Insight) showInsightDetail;
  const _TodayTab({
    required this.mp,
    required this.headlines,
    required this.isLive,
    required this.showInsightDetail,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 120),
      children: [
        _PulseHero(headline: mp.headline),
        const SizedBox(height: 20),
        const SectionHeader(
          title: 'Today\'s Insights',
          subtitle: 'Conclusions you can act on right now',
        ),
        ...List.generate(
          mp.insights.length,
          (i) => Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: InsightCard(
              insight: mp.insights[i],
              index: i,
              onTap: () => showInsightDetail(context, mp.insights[i]),
            ),
          ),
        ),
        const SizedBox(height: 8),
        SectionHeader(
          title: 'Top Headlines',
          subtitle: isLive
              ? 'Live · ${headlines.length} fresh from RSS'
              : 'Loading live feed… curated baseline visible',
        ),
        ...List.generate(
          headlines.length,
          (i) => Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _NewsTile(item: headlines[i], index: i),
          ),
        ),
      ],
    );
  }
}

// ─── Tab 2: My Clients — horizontal scroll of client cards ──────────────────

class _MyClientsTab extends StatelessWidget {
  const _MyClientsTab();

  static const _clients = [
    {'ticker': 'PGR', 'name': 'Progressive Corp.', 'cr': 96.4, 'roe': 31.5, 'risk': 'low', 'type': 'P&C'},
    {'ticker': 'TRV', 'name': 'Travelers Cos.',     'cr': 96.9, 'roe': 17.9, 'risk': 'low', 'type': 'P&C'},
    {'ticker': 'CB',  'name': 'Chubb Limited',      'cr': 86.6, 'roe': 14.9, 'risk': 'low', 'type': 'P&C'},
    {'ticker': 'MET', 'name': 'MetLife Inc.',       'cr': 64.9, 'roe': 12.8, 'risk': 'medium', 'type': 'Life'},
    {'ticker': 'AFL', 'name': 'Aflac Inc.',         'cr': 54.8, 'roe': 19.1, 'risk': 'low', 'type': 'Life'},
    {'ticker': 'UNH', 'name': 'UnitedHealth Group', 'cr': 82.3, 'roe': 23.1, 'risk': 'medium', 'type': 'Health'},
  ];

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(0, 8, 0, 120),
      children: [
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 20),
          child: SectionHeader(
            title: 'Your watchlist',
            subtitle: 'Horizontal scroll · tap a card to drill in',
          ),
        ),
        SizedBox(
          height: 220,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 14),
            itemCount: _clients.length,
            itemBuilder: (context, i) {
              final c = _clients[i];
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 6),
                child: _ClientCard(client: c, index: i),
              );
            },
          ),
        ),
        const SizedBox(height: 18),
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 20),
          child: SectionHeader(
            title: 'Risk flags',
            subtitle: 'Highest-attention items across your book',
          ),
        ),
        for (final f in const [
          {'ticker': 'MET', 'flag': 'LTC unlocking risk under ASC 944-40 LDTI', 'level': 'high'},
          {'ticker': 'UNH', 'flag': 'MLR pressure — close to ACA rebate threshold', 'level': 'medium'},
          {'ticker': 'PGR', 'flag': 'Auto severity inflation may compress CR', 'level': 'medium'},
        ])
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
            child: _RiskFlagCard(flag: f),
          ),
      ],
    );
  }
}

class _ClientCard extends StatelessWidget {
  final Map<String, dynamic> client;
  final int index;
  const _ClientCard({required this.client, required this.index});
  @override
  Widget build(BuildContext context) {
    final riskColor = client['risk'] == 'high'
        ? AppColors.negative
        : client['risk'] == 'medium'
            ? AppColors.warning
            : AppColors.positive;
    return SizedBox(
      width: 200,
      child: GlassCard(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    gradient: AppColors.primaryGradient,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(client['ticker'] as String,
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w800,
                          fontSize: 11,
                          letterSpacing: 0.6)),
                ),
                const Spacer(),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: riskColor,
                    shape: BoxShape.circle,
                    boxShadow: AppColors.glow(riskColor, radius: 6),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(client['name'] as String,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    height: 1.25)),
            const SizedBox(height: 4),
            Text(client['type'] as String,
                style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 10,
                    letterSpacing: 0.6,
                    fontWeight: FontWeight.w700)),
            const Spacer(),
            Row(
              children: [
                Expanded(
                  child: _MiniMetric(
                      label: 'CR', value: '${client['cr']}%'),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _MiniMetric(
                      label: 'ROE', value: '${client['roe']}%'),
                ),
              ],
            ),
          ],
        ),
      ).animate().fadeIn(duration: 280.ms, delay: (60 * index).ms),
    );
  }
}

class _MiniMetric extends StatelessWidget {
  final String label;
  final String value;
  const _MiniMetric({required this.label, required this.value});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceHigh,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 9.5,
                  letterSpacing: 0.8,
                  fontWeight: FontWeight.w700)),
          Text(value,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 12,
                  fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}

class _RiskFlagCard extends StatelessWidget {
  final Map<String, dynamic> flag;
  const _RiskFlagCard({required this.flag});
  @override
  Widget build(BuildContext context) {
    final color = flag['level'] == 'high'
        ? AppColors.negative
        : AppColors.warning;
    return GlassCard(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 38,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(2),
              boxShadow: AppColors.glow(color, radius: 8),
            ),
          ),
          const SizedBox(width: 12),
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: AppColors.surfaceHigh,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(flag['ticker'] as String,
                style: const TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w800,
                    fontSize: 11)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(flag['flag'] as String,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w500,
                    height: 1.35)),
          ),
        ],
      ),
    );
  }
}

// ─── Tab 3: Market Pulse — broader indicators + sparklines ──────────────────

class _MarketPulseTab extends StatelessWidget {
  final MarketPulse mp;
  const _MarketPulseTab({required this.mp});
  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 120),
      children: [
        const SectionHeader(
            title: 'Industry indicators',
            subtitle: 'Tap any indicator for the rationale'),
        _Indicators(
            indicators: mp.indicators, sparklines: mp.sparklines),
        const SizedBox(height: 18),
        const SectionHeader(
            title: 'Insurance industry benchmarks',
            subtitle: '2026 view across P&C, Life, Health'),
        const _BenchmarkRow(label: 'P&C Combined Ratio', value: '98.7%', delta: '+1.1pp YoY', positive: false),
        const _BenchmarkRow(label: 'Life Investment Yield', value: '4.32%', delta: '+18 bps', positive: true),
        const _BenchmarkRow(label: 'Auto Severity YoY', value: '+8.4%', delta: 'Hard market', positive: false),
        const _BenchmarkRow(label: 'MA Star Ratings 4+', value: '42%', delta: '−5pp', positive: false),
        const _BenchmarkRow(label: 'Cat Bond Issuance', value: '\$12.1B', delta: '+18%', positive: true),
      ],
    );
  }
}

class _BenchmarkRow extends StatelessWidget {
  final String label;
  final String value;
  final String delta;
  final bool positive;
  const _BenchmarkRow(
      {required this.label,
      required this.value,
      required this.delta,
      required this.positive});
  @override
  Widget build(BuildContext context) {
    final c = positive ? AppColors.positive : AppColors.warning;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: GlassCard(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(
          children: [
            Expanded(
              child: Text(label,
                  style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontWeight: FontWeight.w700,
                      fontSize: 13)),
            ),
            Text(value,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 14)),
            const SizedBox(width: 10),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: c.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: c.withValues(alpha: 0.5)),
              ),
              child: Text(delta,
                  style: TextStyle(
                      color: c,
                      fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Tab 4: Risk Watch — emerging risks + alerts ───────────────────────────

class _RiskWatchTab extends StatelessWidget {
  const _RiskWatchTab();

  static const _watch = [
    {'icon': '🌪️', 'title': 'Atlantic hurricane season ramping', 'detail': '2026 season pre-formation NOAA outlook above-average — primary-cat retro pricing firming.', 'level': 'high'},
    {'icon': '🔥', 'title': 'California wildfire vegetation index elevated', 'detail': 'Property-cat carriers tightening underwriting in 21 ZIP codes; FAIR Plan exposure up 14% YoY.', 'level': 'high'},
    {'icon': '💻', 'title': 'AI-related claim litigation rising', 'detail': 'D&O and E&O severity up — model-bias-discrimination class actions a leading driver.', 'level': 'medium'},
    {'icon': '🔐', 'title': 'Cyber: ransomware payouts plateau', 'detail': 'Average ransom payment flat YoY but recovery costs accelerate — insurers tightening cyber retentions.', 'level': 'medium'},
    {'icon': '📈', 'title': 'Interest-rate volatility', 'detail': '10Y Treasury swings impacting life-insurer LDTI OCI by hundreds of bps. Monitor AOCI rollforward.', 'level': 'medium'},
    {'icon': '🏛️', 'title': 'NAIC private-credit principles', 'detail': '2026 expansions on credit risk transfer for private credit exposures may reshape life-insurer asset mix.', 'level': 'medium'},
    {'icon': '🏥', 'title': 'Medicare Advantage v28 phase-in', 'detail': 'Risk-adjustment changes compressing MA margins; watch CMS proposed rate notice for 2027.', 'level': 'high'},
  ];

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 120),
      children: [
        const SectionHeader(
            title: 'Emerging risk watch',
            subtitle: 'Drivers to monitor across the US insurance landscape'),
        for (var i = 0; i < _watch.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _RiskWatchCard(item: _watch[i], index: i),
          ),
      ],
    );
  }
}

class _RiskWatchCard extends StatelessWidget {
  final Map<String, dynamic> item;
  final int index;
  const _RiskWatchCard({required this.item, required this.index});
  @override
  Widget build(BuildContext context) {
    final color = item['level'] == 'high'
        ? AppColors.negative
        : AppColors.warning;
    return GlassCard(
      borderColor: color.withValues(alpha: 0.45),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: color.withValues(alpha: 0.45)),
            ),
            alignment: Alignment.center,
            child: Text(item['icon'] as String,
                style: const TextStyle(fontSize: 20)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(item['title'] as String,
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w800,
                              fontSize: 13.5,
                              height: 1.25)),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.16),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                          (item['level'] as String).toUpperCase(),
                          style: TextStyle(
                              color: color,
                              fontSize: 9,
                              letterSpacing: 1.0,
                              fontWeight: FontWeight.w900)),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(item['detail'] as String,
                    style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                        height: 1.45)),
              ],
            ),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 280.ms, delay: (40 * index).ms);
  }
}

class _DashboardHeader extends StatefulWidget {
  final bool isLive;
  const _DashboardHeader({this.isLive = false});

  @override
  State<_DashboardHeader> createState() => _DashboardHeaderState();
}

class _DashboardHeaderState extends State<_DashboardHeader> {
  bool _backendReachable = false;

  @override
  void initState() {
    super.initState();
    _refreshBackend();
  }

  Future<void> _refreshBackend() async {
    if (!ApiService.instance.isConfigured) {
      if (mounted) setState(() => _backendReachable = false);
      return;
    }
    final ok = await ApiService.instance.probe();
    if (mounted) setState(() => _backendReachable = ok);
  }

  @override
  Widget build(BuildContext context) {
    // The pill is LIVE if either the on-device RSS pull succeeded OR
    // a configured backend is responding.
    final live = widget.isLive || _backendReachable;
    final color = live ? AppColors.positive : AppColors.warning;
    final label = live ? 'LIVE' : 'LOADING';
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            gradient: AppColors.primaryGradient,
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.bolt_rounded, color: Colors.white, size: 18),
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
                  fontSize: 20,
                  letterSpacing: -0.4,
                ),
              ),
              Text(
                'Pro · v1.0',
                style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 11,
                    letterSpacing: 1.4,
                    fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
        InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () async {
            await Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => const SettingsScreen()));
            _refreshBackend();
          },
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                      color: color, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(label,
                    style: TextStyle(
                        color: color,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.6)),
                const SizedBox(width: 6),
                const Icon(Icons.settings_outlined,
                    color: AppColors.textMuted, size: 12),
              ],
            ),
          ),
        ),
      ],
    ).animate().fadeIn(duration: 400.ms).slideY(begin: -0.1);
  }
}

class _PulseHero extends StatelessWidget {
  final String headline;
  const _PulseHero({required this.headline});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF1A2A48), Color(0xFF0B1426)],
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'TODAY\'S TAKE',
                  style: TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 11,
                    letterSpacing: 1.6,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  headline,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w700,
                    fontSize: 17,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: AppColors.primaryGradient,
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withValues(alpha: 0.4),
                  blurRadius: 18,
                  spreadRadius: 1,
                ),
              ],
            ),
            child: const Icon(Icons.auto_graph_rounded,
                color: Colors.white, size: 22),
          ),
        ],
      ),
    ).animate().fadeIn(duration: 500.ms).slideY(begin: 0.1);
  }
}

class _Indicators extends StatelessWidget {
  final List<TrendIndicator> indicators;
  final List<List<double>> sparklines;
  const _Indicators({required this.indicators, required this.sparklines});

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: indicators.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.55,
      ),
      itemBuilder: (context, i) {
        final ind = indicators[i];
        final isUp = ind.direction == 'up';
        final positiveSignal = ind.label.contains('Yield') ||
            ind.label.contains('Issuance') ||
            ind.label.contains('Star');
        final color = isUp
            ? (positiveSignal ? AppColors.positive : AppColors.warning)
            : (positiveSignal ? AppColors.warning : AppColors.positive);
        final spark = sparklines.isNotEmpty
            ? sparklines[i % sparklines.length]
            : <double>[];
        return GlassCard(
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
          onTap: () => _showIndicatorDetail(context, ind, spark, color),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(ind.label,
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10.5,
                      letterSpacing: 0.6,
                      fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 6),
              Row(
                children: [
                  Text(ind.value,
                      style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 18,
                          fontWeight: FontWeight.w800)),
                  const SizedBox(width: 8),
                  Icon(
                    isUp
                        ? Icons.arrow_upward_rounded
                        : Icons.arrow_downward_rounded,
                    color: color,
                    size: 14,
                  ),
                  if (ind.delta != null)
                    Text(
                      Formatters.signedPct(ind.delta! * 100),
                      style: TextStyle(
                          color: color,
                          fontSize: 11,
                          fontWeight: FontWeight.w700),
                    ),
                ],
              ),
              const Spacer(),
              if (spark.isNotEmpty)
                Sparkline(data: spark, color: color, height: 24),
            ],
          ),
        ).animate().fadeIn(duration: 300.ms, delay: (60 * i).ms);
      },
    );
  }
}

class _NewsTile extends StatelessWidget {
  final NewsItem item;
  final int index;
  const _NewsTile({required this.item, required this.index});

  Color _impactColor() {
    switch (item.impact) {
      case 'High':
        return AppColors.negative;
      case 'Low':
        return AppColors.textMuted;
      default:
        return AppColors.warning;
    }
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => NewsDetailScreen(item: item))),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 4,
            height: 36,
            decoration: BoxDecoration(
              color: _impactColor(),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(item.source.toUpperCase(),
                        style: const TextStyle(
                            color: AppColors.accent,
                            fontSize: 10,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w700)),
                    const SizedBox(width: 8),
                    Text(item.category,
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                    const Spacer(),
                    Text(Formatters.date(item.published),
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 10.5)),
                  ],
                ),
                const SizedBox(height: 6),
                Text(item.title,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                        height: 1.3)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          const Icon(Icons.arrow_forward_ios_rounded,
              color: AppColors.accent, size: 12),
        ],
      ),
    ).animate().fadeIn(duration: 350.ms, delay: (60 * index).ms);
  }
}

void _showInsightDetail(BuildContext context, Insight insight) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppColors.background,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (_) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(insight.title,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 19,
                    height: 1.25)),
            if ((insight.detail ?? '').isNotEmpty) ...[
              const SizedBox(height: 10),
              Text(insight.detail!,
                  style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 13.5,
                      height: 1.55)),
            ],
            if (insight.tags.isNotEmpty) ...[
              const SizedBox(height: 14),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: insight.tags
                    .map((t) => Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: AppColors.border),
                          ),
                          child: Text(t.toUpperCase(),
                              style: const TextStyle(
                                  color: AppColors.textMuted,
                                  fontSize: 10,
                                  letterSpacing: 1.2,
                                  fontWeight: FontWeight.w700)),
                        ))
                    .toList(),
              ),
            ],
          ],
        ),
      ),
    ),
  );
}

void _showIndicatorDetail(BuildContext context, TrendIndicator ind,
    List<double> spark, Color color) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppColors.background,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (_) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(
                  color: AppColors.surfaceHigh,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(ind.label,
                style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 19)),
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(ind.value,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w800,
                        fontSize: 28)),
                const SizedBox(width: 10),
                if (ind.delta != null)
                  Text(
                    Formatters.signedPct(ind.delta! * 100),
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w700,
                        fontSize: 14),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            if (spark.isNotEmpty)
              SizedBox(
                  height: 80,
                  child: Sparkline(data: spark, color: color, height: 80)),
            const SizedBox(height: 14),
            Text(
              _indicatorRationale(ind.label),
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.55),
            ),
            const SizedBox(height: 14),
            const Text(
              'Source · Treasury / NAIC / industry trade publications · Updated daily.',
              style: TextStyle(color: AppColors.textMuted, fontSize: 11),
            ),
          ],
        ),
      ),
    ),
  );
}

String _indicatorRationale(String label) {
  if (label.contains('10Y Treasury')) {
    return 'The 10-year Treasury yield is the dominant driver of life-insurer book yields and the discount rate underlying GAAP LDTI Liability for Future Policy Benefits. Higher rates lift investment income and lower LFPB through OCI.';
  }
  if (label.contains('Claims Inflation')) {
    return 'P&C claims inflation reflects severity in repair, medical, and litigation costs. Sustained elevation pressures combined ratios and forces rate filings.';
  }
  if (label.contains('Auto Severity')) {
    return 'Auto severity captures the rising cost per claim driven by vehicle complexity, medical inflation, and litigation finance. The single largest driver of personal-lines combined ratio in 2022–2024.';
  }
  if (label.contains('Cat Bond')) {
    return 'Catastrophe-bond issuance is a leading indicator of reinsurance capacity. Strong issuance signals investor appetite for insurance risk and softer retro pricing.';
  }
  if (label.contains('MA Star')) {
    return 'Medicare Advantage Star Ratings determine ~5% bonus payments and marketing limits. Falling 4+ Star share compresses Medicare-Advantage carrier margins and triggers rebate exposure.';
  }
  return 'Macro indicator influencing insurance-industry economics. Watch for trend changes — they typically lag into combined ratios with a 1–2 quarter delay.';
}
