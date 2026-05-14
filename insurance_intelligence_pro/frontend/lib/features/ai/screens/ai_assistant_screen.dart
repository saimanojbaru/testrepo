import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/hyperlinked_text.dart';
import '../../gamification/services/xp_service.dart';

/// Mock AI Auditor Assistant — deterministic prompted responses for the
/// canonical insurance-audit asks: reserve-risk summary, KAM drafts,
/// GAAP vs SAP comparisons.  Responses render through HyperlinkedText
/// so every SSAP/ASC/AS reference in the output is tappable.
class AIAssistantScreen extends StatefulWidget {
  const AIAssistantScreen({super.key});

  @override
  State<AIAssistantScreen> createState() => _AIAssistantScreenState();
}

class _ChatMessage {
  final bool fromUser;
  final String text;
  const _ChatMessage({required this.fromUser, required this.text});
}

class _AIAssistantScreenState extends State<AIAssistantScreen> {
  final TextEditingController _input = TextEditingController();
  final ScrollController _scroll = ScrollController();
  final List<_ChatMessage> _messages = [
    const _ChatMessage(
      fromUser: false,
      text:
          'I\'m your audit assistant. Try a prompt below — or type your own. '
          'Every SSAP / ASC / AS / FAS reference in my reply is tappable.',
    ),
  ];
  bool _thinking = false;

  static const _suggestions = [
    'Summarize reserve risks from a 10-K',
    'Draft KAM for reinsurance recoverables',
    'GAAP vs SAP differences for DAC',
    'Risk-transfer test (10/10 rule) summary',
    'Walk me through ORSA scope',
    'Compare ASC 944-30 vs SSAP non-admit DAC',
  ];

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send(String text) async {
    final clean = text.trim();
    if (clean.isEmpty) return;
    setState(() {
      _messages.add(_ChatMessage(fromUser: true, text: clean));
      _thinking = true;
      _input.clear();
    });
    _scrollToBottom();
    await Future.delayed(const Duration(milliseconds: 700));
    final reply = _generateReply(clean);
    setState(() {
      _messages.add(_ChatMessage(fromUser: false, text: reply));
      _thinking = false;
    });
    _scrollToBottom();
    if (clean.toLowerCase().contains('kam')) {
      await XpService.instance.recordEvent('ai_kam_drafted', xpDelta: 25);
    } else {
      await XpService.instance.recordEvent('ai_prompt_sent', xpDelta: 5);
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic);
      }
    });
  }

  /// Rule-based reply generator.
  String _generateReply(String prompt) {
    final p = prompt.toLowerCase();
    if (p.contains('reserve') && p.contains('risk')) {
      return _kReserveRiskSummary;
    }
    if (p.contains('kam') && p.contains('reinsurance')) {
      return _kKamReinsuranceDraft;
    }
    if (p.contains('kam') && p.contains('reserve')) {
      return _kKamReserveDraft;
    }
    if (p.contains('kam')) {
      return _kKamGenericDraft;
    }
    if ((p.contains('gaap') && p.contains('sap')) ||
        (p.contains('stat') && p.contains('gaap'))) {
      if (p.contains('dac')) return _kGaapSapDac;
      if (p.contains('reserve')) return _kGaapSapReserves;
      if (p.contains('reinsurance')) return _kGaapSapReinsurance;
      return _kGaapSapOverview;
    }
    if (p.contains('risk-transfer') ||
        p.contains('risk transfer') ||
        p.contains('10/10')) {
      return _kRiskTransfer;
    }
    if (p.contains('orsa')) {
      return _kOrsaScope;
    }
    if (p.contains('10-k') || p.contains('10k')) {
      return _kTenKSummary;
    }
    return 'I can help with reserve-risk summaries, KAM drafts, '
        'GAAP vs SAP comparisons, risk-transfer assessments, and 10-K '
        'walk-throughs. Try one of the suggested prompts above — or '
        'reference a specific standard like SSAP 55, ASC 944-40, or AS 2501 '
        'and I\'ll tailor the response.';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Row(
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                gradient: AppColors.primaryGradient,
                borderRadius: BorderRadius.circular(8),
              ),
              alignment: Alignment.center,
              child:
                  const Icon(Icons.auto_awesome, color: Colors.white, size: 16),
            ),
            const SizedBox(width: 10),
            const Text('AI Auditor',
                style: TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w800,
                    fontSize: 16)),
            const SizedBox(width: 6),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.warning.withValues(alpha: 0.16),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(
                    color: AppColors.warning.withValues(alpha: 0.5)),
              ),
              child: const Text('SIMULATED',
                  style: TextStyle(
                      color: AppColors.warning,
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2)),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              itemCount: _messages.length + (_thinking ? 1 : 0),
              itemBuilder: (context, i) {
                if (i == _messages.length) return const _ThinkingBubble();
                return _MessageBubble(message: _messages[i]);
              },
            ),
          ),
          // Suggestion chips
          SizedBox(
            height: 38,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              scrollDirection: Axis.horizontal,
              children: [
                for (final s in _suggestions)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ActionChip(
                      backgroundColor: AppColors.surfaceElevated,
                      side: const BorderSide(color: AppColors.border),
                      label: Text(s,
                          style: const TextStyle(
                              color: AppColors.textPrimary,
                              fontSize: 11.5,
                              fontWeight: FontWeight.w600)),
                      onPressed: () => _send(s),
                    ),
                  ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(14, 8, 14, 16),
            decoration: const BoxDecoration(
              color: AppColors.background,
              border: Border(top: BorderSide(color: AppColors.divider)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _input,
                    onSubmitted: _send,
                    textInputAction: TextInputAction.send,
                    style: const TextStyle(
                        color: AppColors.textPrimary, fontSize: 13),
                    decoration: const InputDecoration(
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(
                          horizontal: 14, vertical: 12),
                      hintText: 'Ask anything insurance-audit…',
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: () => _send(_input.text),
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      gradient: AppColors.primaryGradient,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(Icons.send_rounded,
                        color: Colors.white, size: 18),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final _ChatMessage message;
  const _MessageBubble({required this.message});
  @override
  Widget build(BuildContext context) {
    final isUser = message.fromUser;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 6),
        constraints: BoxConstraints(
            maxWidth: MediaQuery.of(context).size.width * 0.86),
        child: GlassCard(
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
          gradient: isUser
              ? const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF1F4FFF), Color(0xFF00E0C7)])
              : null,
          child: HyperlinkedText(
            message.text,
            style: TextStyle(
                color: isUser ? Colors.white : AppColors.textPrimary,
                fontSize: 13,
                height: 1.5,
                fontWeight: FontWeight.w500),
          ),
        ),
      ).animate().fadeIn(duration: 240.ms),
    );
  }
}

class _ThinkingBubble extends StatelessWidget {
  const _ThinkingBubble();
  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: GlassCard(
          padding:
              const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 0; i < 3; i++)
                Container(
                  width: 6,
                  height: 6,
                  margin: EdgeInsets.only(right: i == 2 ? 0 : 4),
                  decoration: const BoxDecoration(
                    color: AppColors.accent,
                    shape: BoxShape.circle,
                  ),
                ).animate(onPlay: (c) => c.repeat())
                    .fadeIn(duration: 400.ms, delay: (120 * i).ms)
                    .then()
                    .fadeOut(duration: 400.ms),
            ],
          ),
        ),
      ),
    );
  }
}

// ============================================================================
// Mocked reply templates — all references intentionally render as deep links
// ============================================================================

const String _kReserveRiskSummary = '''Reserve-risk summary for a US P&C insurer 10-K:

1. Loss & LAE adequacy
   • Compare booked reserves to actuarial central estimate; SSAP 55 / ASC 944-40 require best-estimate measurement.
   • Read Schedule P loss-development triangles for adverse trend signals.
   • Watch for "social inflation" pressure on liability lines (GL, commercial auto, med-mal).

2. LDTI exposure
   • If the company writes long-duration life or LTC, ASC 944-40 cohort-level LFPB and annual unlocking are key risks.
   • Review the 944-40 net-premium-ratio disclosure for cohorts approaching the 100% cap.

3. Discount-rate sensitivity (Life)
   • Upper-medium-grade discount-rate updates flow through OCI; significant rate moves can swing AOCI by hundreds of millions.

4. CECL on reinsurance recoverables
   • ASC 326-20 expected-credit-loss allowance is now required; watch for concentration concerns.

5. Climate & cat exposure
   • SEC climate rule + NAIC SSAP 119 require disclosure of physical and transition risk.

Drill in by tapping any reference above.''';

const String _kKamReinsuranceDraft = '''Critical Audit Matter — Reinsurance Recoverables (draft, AS 3101)

Description of the Matter
As discussed in Note X, the Company's reinsurance recoverables on paid and unpaid losses totaled \$X.X billion at December 31. The recoverable is measured under ASC 944-310, net of the credit-loss allowance required by ASC 326-20. Schedule F under SSAP 62R / SSAP 61R is the statutory analog. Authorized vs unauthorized status and slow-pay provisions can drive material non-admission.

Why the matter was a CAM
Auditing the recoverable required significant auditor judgment in evaluating (a) the appropriateness of the credit-loss methodology, (b) inputs including reinsurer credit ratings, recovery duration, and collateral, and (c) concentration with the Company's top reinsurers.

How we addressed the matter
We obtained an understanding of and tested controls over the credit-loss methodology and the Schedule F process. We tested the inputs through reinsurer-financial-strength evidence, confirmation balances (AS 1105), and recomputation of the allowance. We engaged credit specialists per AS 1210 to evaluate the reasonableness of the assumed loss-given-default factors.''';

const String _kKamReserveDraft = '''Critical Audit Matter — Liability for Unpaid Claims and Loss Adjustment Expense (draft, AS 3101)

Description of the Matter
The Company's liability for unpaid claims and LAE totaled \$X.X billion at year-end and is measured under ASC 944-40 (GAAP) and SSAP 55 (STAT). The reserve includes case, bulk, IBNR, ALAE, and ULAE components. Long-tail lines (workers' comp, GL, med-mal) involve significant judgment.

Why the matter was a CAM
Auditing required substantial judgment to evaluate management's actuarial central estimate, model assumptions, and reserve-development trends disclosed in the ASU 2015-09 tables.

How we addressed the matter
We tested ITGCs and management review controls over the actuarial process. We engaged firm actuaries under AS 1210 / AS 2501 to develop an independent reserve range using chain-ladder, Bornhuetter-Ferguson, and stochastic methods. We compared management's booked estimate to our independent range and evaluated reserve development from prior accident years per Schedule P / ASU 2015-09.''';

const String _kKamGenericDraft = '''Pick a Critical Audit Matter topic and I\'ll draft an AS 3101-style narrative. Common KAMs for insurance audits include:

• Liability for Unpaid Claims & LAE — methodology under SSAP 55 / ASC 944-40.
• Reinsurance Recoverables — CECL allowance under ASC 326-20 + Schedule F.
• Long-Duration LFPB — cohort-level measurement under ASC 944-40 (LDTI).
• Market Risk Benefits — fair-value measurement under ASC 944-40-30-19A.
• LTC Reserve Adequacy — premium-deficiency / net-premium-ratio cap.
• Goodwill & VOBA — ASC 805 / SSAP 68 admission limits.''';

const String _kGaapSapDac = '''GAAP vs SAP — Deferred Acquisition Costs (DAC)

GAAP (ASC 944-30, post-LDTI)
• Capitalize incremental + direct + successful acquisition costs (first-year commissions in excess of renewal, underwriting, contract-issuance costs).
• Amortize on a constant-level basis tied to a denominator (e.g., inforce face), no interest accrual.
• Annual review of denominator assumption — prospective only.
• DAC balance presented as a separate asset on the balance sheet.

SAP (SSAP 4 + SSAP 71)
• No DAC asset. All acquisition costs expensed when incurred.
• Drives the classic "new-business strain" — fast-growing life writers can show GAAP profits while burning statutory surplus.

Key delta
• Pure GAAP construct. STAT writes off acquisition costs immediately, producing the largest single GAAP-vs-STAT asset difference.''';

const String _kGaapSapReserves = '''GAAP vs SAP — Loss Reserves (P&C)

GAAP (ASC 944-40 / SSAP 55)
• Both frameworks measure unpaid claims at best estimate, undiscounted (with the limited workers'-comp tabular-discount exception).
• GAAP disclosure: ASU 2015-09 triangle tables for the latest ten accident years.
• Tabular discount on WC indemnity tabular cases permitted with commissioner approval (STAT).

Key delta
• Largely aligned in measurement. The Schedule P statutory disclosure is more granular (line-of-business detail) than the ASU 2015-09 GAAP disclosure.
• RBC R5 implicitly captures reserve uncertainty on the STAT side.''';

const String _kGaapSapReinsurance = '''GAAP vs SAP — Reinsurance

GAAP (ASC 944-20 + ASC 944-310 + ASC 326-20)
• Risk-transfer test in ASC 944-20-15-41 governs reinsurance vs deposit accounting.
• Reinsurance recoverables presented as a separate asset, net of CECL allowance.

SAP (SSAP 61R / SSAP 62R + Schedule F)
• Same risk-transfer test (SSAP 62R Appendix A).
• Authorized vs unauthorized reinsurer status drives non-admission of uncollateralized recoverables.
• 20% slow-pay provision applies to balances 90+ days past due.

Key deltas
• GAAP recognizes credit deterioration earlier via CECL; STAT relies on the authorized/unauthorized + slow-pay framework with cliff effects.''';

const String _kGaapSapOverview = '''GAAP vs SAP — Big-picture overview

GAAP (ASC 944 / FASB)
• Going-concern, income-statement-focused.
• Capitalizes DAC, VOBA; presents fair value on AFS securities.
• Discount life reserves using upper-medium-grade A-rate (post-LDTI).

SAP (NAIC AP&P Manual)
• Liquidation-basis, solvency-focused.
• Non-admits DAC, prepaid expenses, furniture, restricted cash.
• Holds high-quality bonds at amortized cost, regardless of intent.
• AVR + IMR contingency reserves reduce surplus.
• RBC ratio = Total Adjusted Capital / Authorized Control Level.

Bottom line
• STAT surplus is almost always lower than GAAP equity for the same entity. Use STAT for solvency, GAAP for earnings analysis.''';

const String _kRiskTransfer = '''Risk-Transfer Test summary (ASC 944-20-15-41)

Two prongs to qualify for reinsurance accounting:
1. Reinsurer must assume significant insurance risk (both underwriting AND timing risk).
2. There must be a reasonable possibility the reinsurer will recognize a significant loss.

The informal "10/10 rule" — 10% probability of 10% economic loss — is heuristic guidance, not regulation.

Common failure modes
• Aggregate caps too low.
• Profit-sharing or experience-rating refunds that reverse reinsurer losses.
• Over-collateralization that defeats credit risk.

If risk transfer fails
• Treat as a deposit. Cedent records cash received as a liability, retains gross loss reserves, recognizes no underwriting gain/loss. Interest accretes on the deposit.

STAT mirror
• SSAP 62R Appendix A — same conceptual test. Appointed actuary certification required for retrospective contracts.''';

const String _kOrsaScope = '''ORSA — Own Risk and Solvency Assessment scope

Trigger
• US insurance groups with > \$1B annual premium must file an annual ORSA Summary Report with the lead state regulator under the NAIC ORSA Model Act.

Three sections
1. Risk Management Framework — governance, appetite, identification.
2. Risk Profile — insurance, market, credit, liquidity, operational, strategic, reputation.
3. Group Solvency Assessment — capital projections under base + stress over multi-year horizon.

Companion exercises
• Group Capital Calculation (GCC) aggregates capital across the group.
• Liquidity Stress Test (LST) required for ~30 large life insurers.

GAAP analog
• None directly. SEC disclosure (Item 1A, 7A) and rating-agency capital models partially overlap.''';

const String _kTenKSummary = '''10-K analyst walkthrough — insurance-specific risk read

1. Item 1A Risk Factors — climate / catastrophe, social inflation, interest-rate, cyber, regulatory.
2. Item 7 MD&A — combined ratio decomposition, reserve development, investment portfolio mix.
3. Item 7A Market Risk — duration mismatch, equity-market sensitivity for life insurers.
4. Notes
   • Long-duration LFPB rollforward and net-premium-ratio movement (ASC 944-40 + ASU 2018-12).
   • DAC rollforward (ASC 944-30).
   • Reinsurance recoverable concentration + CECL allowance.
   • ASU 2015-09 P&C development triangles.
   • Fair-value hierarchy and Level 3 sensitivities.
5. Item 9A ICFR — auditor opinion plus material-weakness disclosures.
6. AS 3101 Auditor's Report — CAMs to look for: reserves, MRBs, reinsurance recoverables, LTC adequacy, goodwill.''';
