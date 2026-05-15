import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/hyperlinked_text.dart';
import '../../gamification/services/xp_service.dart';
import '../state/quiz_state.dart';

/// Full-immersive quiz play screen. The system nav is hidden, the
/// status bar fades, and a dramatic countdown ring drives tension.
class QuizPlayScreen extends StatefulWidget {
  final String categoryKey;
  const QuizPlayScreen({super.key, required this.categoryKey});
  @override
  State<QuizPlayScreen> createState() => _QuizPlayScreenState();
}

class _QuizPlayScreenState extends State<QuizPlayScreen>
    with TickerProviderStateMixin {
  late final List<QuizQuestion> _round;
  int _idx = 0;
  int _correct = 0;
  int _combo = 0;
  int _bestCombo = 0;
  int _score = 0;
  int? _selected;
  bool _revealed = false;

  // Per-question countdown (ms remaining)
  static const int _questionMs = 20000;
  int _remainingMs = _questionMs;
  Timer? _ticker;

  // Wrong-answer shake controller
  late final AnimationController _shake = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 420));

  // Celebratory burst controller
  late final AnimationController _burst = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 900));

  @override
  void initState() {
    super.initState();
    _round = QuizState.instance.buildRound(widget.categoryKey);
    // Immersive: hide system status / nav bars while in play screen.
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _startTicker();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _shake.dispose();
    _burst.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _startTicker() {
    _ticker?.cancel();
    _remainingMs = _questionMs;
    _ticker = Timer.periodic(const Duration(milliseconds: 100), (_) {
      if (!mounted) return;
      setState(() {
        _remainingMs = (_remainingMs - 100).clamp(0, _questionMs);
      });
      if (_remainingMs == 0 && !_revealed) {
        // Time out — auto-reveal as a miss.
        _selected ??= -1;
        _reveal();
      }
    });
  }

  Color _timerColor() {
    final pct = _remainingMs / _questionMs;
    if (pct > 0.5) return AppColors.accent;
    if (pct > 0.25) return AppColors.warning;
    return AppColors.negative;
  }

  @override
  Widget build(BuildContext context) {
    if (_round.isEmpty) {
      return Scaffold(
        backgroundColor: AppColors.background,
        body: const Center(
          child: Text('No questions in this category yet.',
              style: TextStyle(color: AppColors.textMuted)),
        ),
      );
    }
    final q = _round[_idx];
    final timerColor = _timerColor();
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          // Ambient radial glow that pulses with the timer.
          Positioned.fill(child: _AmbientGlow(color: timerColor)),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PlayHeader(
                    idx: _idx,
                    total: _round.length,
                    score: _score,
                    combo: _combo,
                    timerColor: timerColor,
                    remainingMs: _remainingMs,
                    totalMs: _questionMs,
                    onExit: () => Navigator.of(context).maybePop(),
                  ),
                  const SizedBox(height: 14),
                  // Question card
                  Expanded(
                    child: AnimatedBuilder(
                      animation: _shake,
                      builder: (context, child) {
                        final dx = math.sin(_shake.value * math.pi * 6) *
                            (1 - _shake.value) *
                            14;
                        return Transform.translate(
                            offset: Offset(dx, 0), child: child);
                      },
                      child: SingleChildScrollView(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            _QuestionCard(question: q),
                            const SizedBox(height: 12),
                            for (var i = 0; i < q.choices.length; i++)
                              Padding(
                                padding:
                                    const EdgeInsets.only(bottom: 10),
                                child: _ImmersiveChoice(
                                  label: q.choices[i],
                                  isSelected: _selected == i,
                                  isCorrect:
                                      _revealed && i == q.answer,
                                  isWrong: _revealed &&
                                      _selected == i &&
                                      i != q.answer,
                                  onTap: _revealed
                                      ? null
                                      : () =>
                                          setState(() => _selected = i),
                                  index: i,
                                ),
                              ),
                            if (_revealed) ...[
                              const SizedBox(height: 6),
                              _ExplanationCard(
                                  q: q, correct: _selected == q.answer),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  _ActionBar(
                    revealed: _revealed,
                    isLast: _idx + 1 >= _round.length,
                    canSubmit: _selected != null,
                    onSubmit: _reveal,
                    onNext: _next,
                  ),
                ],
              ),
            ),
          ),
          // Confetti burst on correct
          IgnorePointer(
            child: AnimatedBuilder(
              animation: _burst,
              builder: (_, __) => CustomPaint(
                painter: _BurstPainter(progress: _burst.value),
                size: Size.infinite,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _reveal() {
    if (_revealed) return;
    _ticker?.cancel();
    final q = _round[_idx];
    final isCorrect = _selected == q.answer;
    if (isCorrect) {
      _correct += 1;
      _combo += 1;
      if (_combo > _bestCombo) _bestCombo = _combo;
      // Score = base 100, +50 per combo step, +time bonus.
      final timeBonus = (_remainingMs / _questionMs * 100).toInt();
      _score += 100 + (_combo - 1) * 50 + timeBonus;
      _burst
        ..reset()
        ..forward();
      XpService.instance
          .recordEvent('quiz_answer_correct', xpDelta: 10);
    } else {
      _combo = 0;
      _shake
        ..reset()
        ..forward();
      XpService.instance.recordEvent('quiz_answer_wrong', xpDelta: 0);
    }
    setState(() => _revealed = true);
  }

  void _next() {
    if (_idx + 1 < _round.length) {
      setState(() {
        _idx += 1;
        _selected = null;
        _revealed = false;
      });
      _startTicker();
      return;
    }
    _finish();
  }

  Future<void> _finish() async {
    await QuizState.instance.recordResult(
        categoryKey: widget.categoryKey,
        correct: _correct,
        total: _round.length);
    if (_correct == _round.length) {
      await XpService.instance.recordEvent('quiz_won', xpDelta: 50);
      if (widget.categoryKey == 'reserves') {
        await XpService.instance
            .recordEvent('quiz_streak_loss_reserves', xpDelta: 30);
      }
    }
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(
        builder: (_) => _ResultScreen(
              correct: _correct,
              total: _round.length,
              score: _score,
              bestCombo: _bestCombo,
              categoryKey: widget.categoryKey,
            )));
  }
}

// ─── Header ────────────────────────────────────────────────────────────────

class _PlayHeader extends StatelessWidget {
  final int idx, total, score, combo, remainingMs, totalMs;
  final Color timerColor;
  final VoidCallback onExit;
  const _PlayHeader({
    required this.idx,
    required this.total,
    required this.score,
    required this.combo,
    required this.timerColor,
    required this.remainingMs,
    required this.totalMs,
    required this.onExit,
  });

  @override
  Widget build(BuildContext context) {
    final pct = remainingMs / totalMs;
    return Row(
      children: [
        GestureDetector(
          onTap: onExit,
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.border),
            ),
            child: const Icon(Icons.close_rounded,
                color: AppColors.textPrimary, size: 18),
          ),
        ),
        const SizedBox(width: 12),
        // Countdown ring
        _CountdownRing(
          progress: pct,
          color: timerColor,
          label: '${(remainingMs / 1000).ceil()}',
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Q${idx + 1} / $total',
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontWeight: FontWeight.w700,
                      fontSize: 11,
                      letterSpacing: 1.4)),
              const SizedBox(height: 4),
              Text('$score pts',
                  style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.4)),
            ],
          ),
        ),
        if (combo >= 2)
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.warning.withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(20),
              boxShadow: AppColors.glow(AppColors.warning, radius: 10),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.local_fire_department_rounded,
                    color: AppColors.warning, size: 14),
                const SizedBox(width: 4),
                Text('×$combo',
                    style: const TextStyle(
                        color: AppColors.warning,
                        fontWeight: FontWeight.w900,
                        fontSize: 13)),
              ],
            ),
          ).animate(onPlay: (c) => c.repeat(reverse: true))
              .scale(begin: const Offset(1, 1), end: const Offset(1.08, 1.08), duration: 700.ms),
      ],
    );
  }
}

class _CountdownRing extends StatelessWidget {
  final double progress;
  final Color color;
  final String label;
  const _CountdownRing(
      {required this.progress, required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 44,
      height: 44,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Backdrop ring
          SizedBox(
            width: 44,
            height: 44,
            child: CircularProgressIndicator(
              value: 1.0,
              strokeWidth: 4,
              backgroundColor: AppColors.surfaceHigh,
              color: AppColors.surfaceHigh,
            ),
          ),
          // Live ring (color-shifting)
          SizedBox(
            width: 44,
            height: 44,
            child: CircularProgressIndicator(
              value: progress,
              strokeWidth: 4,
              backgroundColor: Colors.transparent,
              color: color,
            ),
          ),
          Text(label,
              style: TextStyle(
                  color: color,
                  fontSize: 13,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -0.2)),
        ],
      ),
    );
  }
}

// ─── Question card ─────────────────────────────────────────────────────────

class _QuestionCard extends StatelessWidget {
  final QuizQuestion question;
  const _QuestionCard({required this.question});
  @override
  Widget build(BuildContext context) {
    return GlassCard(
      gradient: AppColors.heroGradient,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _Pill(
                  label: _typeLabel(question.type),
                  color: AppColors.accent),
              const SizedBox(width: 6),
              _Pill(
                  label: question.difficulty.toUpperCase(),
                  color: _difficultyColor(question.difficulty)),
            ],
          ),
          const SizedBox(height: 12),
          HyperlinkedText(question.prompt,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                  height: 1.3)),
        ],
      ),
    );
  }

  String _typeLabel(String t) =>
      ({'mc': 'MULTIPLE CHOICE', 'tf': 'TRUE / FALSE', 'scenario': 'SCENARIO'}[t] ?? t.toUpperCase());

  Color _difficultyColor(String d) => d == 'easy'
      ? AppColors.positive
      : d == 'hard'
          ? AppColors.negative
          : AppColors.warning;
}

class _Pill extends StatelessWidget {
  final String label;
  final Color color;
  const _Pill({required this.label, required this.color});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.45)),
      ),
      child: Text(label,
          style: TextStyle(
              color: color,
              fontSize: 9.5,
              letterSpacing: 1.0,
              fontWeight: FontWeight.w800)),
    );
  }
}

// ─── Choice ────────────────────────────────────────────────────────────────

class _ImmersiveChoice extends StatelessWidget {
  final String label;
  final bool isSelected, isCorrect, isWrong;
  final VoidCallback? onTap;
  final int index;
  const _ImmersiveChoice({
    required this.label,
    required this.isSelected,
    required this.isCorrect,
    required this.isWrong,
    required this.onTap,
    required this.index,
  });

  @override
  Widget build(BuildContext context) {
    Color border = AppColors.border;
    Color glow = Colors.transparent;
    final highlight = isCorrect || isWrong;
    if (isCorrect) {
      border = AppColors.positive;
      glow = AppColors.positive;
    } else if (isWrong) {
      border = AppColors.negative;
      glow = AppColors.negative;
    } else if (isSelected) {
      border = AppColors.accent;
      glow = AppColors.accent;
    }
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        boxShadow: glow == Colors.transparent
            ? null
            : AppColors.glow(glow, radius: highlight ? 18 : 10),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
            decoration: BoxDecoration(
              gradient: AppColors.cardGradient,
              border: Border.all(color: border, width: 1.4),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              children: [
                Container(
                  width: 26,
                  height: 26,
                  decoration: BoxDecoration(
                    color: border.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                        color: border.withValues(alpha: 0.5)),
                  ),
                  alignment: Alignment.center,
                  child: Text(String.fromCharCode(65 + index),
                      style: TextStyle(
                          color: border,
                          fontWeight: FontWeight.w900,
                          fontSize: 12)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(label,
                      style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          height: 1.35)),
                ),
                if (isCorrect)
                  const Icon(Icons.check_circle,
                      color: AppColors.positive, size: 20),
                if (isWrong)
                  const Icon(Icons.cancel,
                      color: AppColors.negative, size: 20),
              ],
            ),
          ),
        ),
      ),
    ).animate().fadeIn(duration: 200.ms, delay: (60 * index).ms);
  }
}

// ─── Explanation card ──────────────────────────────────────────────────────

class _ExplanationCard extends StatelessWidget {
  final QuizQuestion q;
  final bool correct;
  const _ExplanationCard({required this.q, required this.correct});
  @override
  Widget build(BuildContext context) {
    final c = correct ? AppColors.positive : AppColors.negative;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.withValues(alpha: 0.08),
        border: Border.all(color: c.withValues(alpha: 0.45), width: 1.2),
        borderRadius: BorderRadius.circular(14),
        boxShadow: AppColors.glow(c, radius: 12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(correct ? Icons.celebration_rounded : Icons.replay_rounded,
                  color: c, size: 18),
              const SizedBox(width: 8),
              Text(correct ? 'NAILED IT' : 'NOT QUITE',
                  style: TextStyle(
                      color: c,
                      fontWeight: FontWeight.w900,
                      fontSize: 12,
                      letterSpacing: 1.6)),
            ],
          ),
          const SizedBox(height: 8),
          HyperlinkedText(q.explanation,
              style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 12.5,
                  height: 1.55)),
        ],
      ),
    ).animate().fadeIn(duration: 280.ms).slideY(begin: 0.06);
  }
}

// ─── Action bar ────────────────────────────────────────────────────────────

class _ActionBar extends StatelessWidget {
  final bool revealed;
  final bool isLast;
  final bool canSubmit;
  final VoidCallback onSubmit;
  final VoidCallback onNext;
  const _ActionBar({
    required this.revealed,
    required this.isLast,
    required this.canSubmit,
    required this.onSubmit,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final label = revealed ? (isLast ? 'Finish' : 'Next') : 'Submit';
    final action = revealed ? onNext : (canSubmit ? onSubmit : null);
    return GestureDetector(
      onTap: action,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        height: 56,
        decoration: BoxDecoration(
          gradient: action == null
              ? const LinearGradient(
                  colors: [Color(0xFF2A3855), Color(0xFF1A2A4D)])
              : AppColors.primaryGradient,
          borderRadius: BorderRadius.circular(16),
          boxShadow: action == null
              ? null
              : AppColors.glow(AppColors.accent, radius: 18),
        ),
        alignment: Alignment.center,
        child: Text(label,
            style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w900,
                fontSize: 16,
                letterSpacing: 0.8)),
      ),
    );
  }
}

// ─── Ambient glow & confetti burst ────────────────────────────────────────

class _AmbientGlow extends StatelessWidget {
  final Color color;
  const _AmbientGlow({required this.color});
  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Positioned(
          top: -140,
          right: -100,
          child: Container(
            width: 320,
            height: 320,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                  colors: [color.withValues(alpha: 0.18), Colors.transparent]),
            ),
          ),
        ),
        Positioned(
          bottom: -160,
          left: -120,
          child: Container(
            width: 360,
            height: 360,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(colors: [
                AppColors.violet.withValues(alpha: 0.14),
                Colors.transparent
              ]),
            ),
          ),
        ),
      ],
    );
  }
}

class _BurstPainter extends CustomPainter {
  final double progress; // 0..1
  _BurstPainter({required this.progress});

  static final _colors = [
    AppColors.accent,
    AppColors.violet,
    AppColors.positive,
    AppColors.warning,
  ];

  @override
  void paint(Canvas canvas, Size size) {
    if (progress == 0) return;
    final center = Offset(size.width / 2, size.height * 0.4);
    final rng = math.Random(42);
    final paint = Paint()..style = PaintingStyle.fill;
    for (var i = 0; i < 36; i++) {
      final angle = (i / 36) * math.pi * 2 + rng.nextDouble() * 0.4;
      final radius = (60 + rng.nextDouble() * 80) * progress;
      final pos = center +
          Offset(math.cos(angle) * radius, math.sin(angle) * radius);
      paint.color = _colors[i % _colors.length]
          .withValues(alpha: (1.0 - progress) * 0.95);
      canvas.drawCircle(pos, 3 + rng.nextDouble() * 3, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _BurstPainter old) => old.progress != progress;
}

// ─── Result screen ─────────────────────────────────────────────────────────

class _ResultScreen extends StatelessWidget {
  final int correct, total, score, bestCombo;
  final String categoryKey;
  const _ResultScreen({
    required this.correct,
    required this.total,
    required this.score,
    required this.bestCombo,
    required this.categoryKey,
  });

  @override
  Widget build(BuildContext context) {
    final perfect = correct == total;
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          Positioned.fill(
            child: _AmbientGlow(
                color: perfect ? AppColors.positive : AppColors.warning),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
              child: Column(
                children: [
                  const SizedBox(height: 18),
                  Container(
                    width: 150,
                    height: 150,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: perfect
                          ? AppColors.primaryGradient
                          : AppColors.heroGradient,
                      boxShadow: AppColors.glow(
                          perfect ? AppColors.positive : AppColors.accent,
                          radius: 30),
                    ),
                    alignment: Alignment.center,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text('$correct/$total',
                            style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w900,
                                fontSize: 38,
                                letterSpacing: -0.8)),
                        Text(perfect ? 'PERFECT' : 'COMPLETE',
                            style: TextStyle(
                                color: Colors.white
                                    .withValues(alpha: 0.86),
                                fontWeight: FontWeight.w800,
                                fontSize: 11,
                                letterSpacing: 2.2)),
                      ],
                    ),
                  ).animate().scale(
                      begin: const Offset(0.6, 0.6),
                      end: const Offset(1, 1),
                      duration: 500.ms,
                      curve: Curves.easeOutBack),
                  const SizedBox(height: 24),
                  Text(
                      perfect ? 'Flawless Victory' : 'Solid Round',
                      style: TextStyle(
                          color: perfect
                              ? AppColors.positive
                              : AppColors.textPrimary,
                          fontWeight: FontWeight.w900,
                          fontSize: 24,
                          letterSpacing: -0.4)),
                  const SizedBox(height: 18),
                  Row(
                    children: [
                      Expanded(
                          child: _StatTile(
                              label: 'Score',
                              value: '$score',
                              color: AppColors.accent)),
                      const SizedBox(width: 10),
                      Expanded(
                          child: _StatTile(
                              label: 'Best combo',
                              value: '×$bestCombo',
                              color: AppColors.warning)),
                    ],
                  ),
                  const Spacer(),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(
                                vertical: 14),
                            side: const BorderSide(
                                color: AppColors.border),
                            foregroundColor: AppColors.accent,
                          ),
                          onPressed: () =>
                              Navigator.of(context).pop(),
                          child: const Text('Back to Arena',
                              style: TextStyle(
                                  fontWeight: FontWeight.w800)),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            boxShadow: AppColors.glow(
                                AppColors.accent,
                                radius: 14),
                          ),
                          child: FilledButton(
                            style: FilledButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                  vertical: 14),
                              backgroundColor: AppColors.primary,
                            ),
                            onPressed: () =>
                                Navigator.of(context).pushReplacement(
                              MaterialPageRoute(
                                  builder: (_) => QuizPlayScreen(
                                      categoryKey: categoryKey)),
                            ),
                            child: const Text('Play again',
                                style: TextStyle(
                                    fontWeight: FontWeight.w900)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  final String label, value;
  final Color color;
  const _StatTile(
      {required this.label, required this.value, required this.color});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        border: Border.all(color: color.withValues(alpha: 0.45)),
        borderRadius: BorderRadius.circular(14),
        boxShadow: AppColors.glow(color, radius: 10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(),
              style: TextStyle(
                  color: color,
                  fontSize: 10,
                  letterSpacing: 1.4,
                  fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          Text(value,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w900,
                  fontSize: 22,
                  letterSpacing: -0.4)),
        ],
      ),
    );
  }
}
