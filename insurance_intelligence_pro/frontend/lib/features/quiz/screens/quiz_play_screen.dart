import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../../../theme/app_colors.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/hyperlinked_text.dart';
import '../../gamification/services/xp_service.dart';
import '../state/quiz_state.dart';

class QuizPlayScreen extends StatefulWidget {
  final String categoryKey;
  const QuizPlayScreen({super.key, required this.categoryKey});
  @override
  State<QuizPlayScreen> createState() => _QuizPlayScreenState();
}

class _QuizPlayScreenState extends State<QuizPlayScreen> {
  late final List<QuizQuestion> _round;
  int _idx = 0;
  int _correct = 0;
  int? _selected;
  bool _revealed = false;

  @override
  void initState() {
    super.initState();
    _round = QuizState.instance.buildRound(widget.categoryKey);
  }

  @override
  Widget build(BuildContext context) {
    if (_round.isEmpty) {
      return Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(
          backgroundColor: AppColors.background,
          elevation: 0,
          iconTheme: const IconThemeData(color: AppColors.textPrimary),
        ),
        body: const Center(
          child: Text('No questions in this category yet.',
              style: TextStyle(color: AppColors.textMuted)),
        ),
      );
    }
    final q = _round[_idx];
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: Text('Q${_idx + 1} / ${_round.length}',
            style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 16)),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.border),
              ),
              child: Text('Score $_correct',
                  style: const TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w800,
                      fontSize: 11)),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
        children: [
          Container(
            height: 6,
            decoration: BoxDecoration(
              color: AppColors.surfaceHigh,
              borderRadius: BorderRadius.circular(3),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: (_idx + 1) / _round.length,
                backgroundColor: Colors.transparent,
                valueColor:
                    const AlwaysStoppedAnimation(AppColors.accent),
              ),
            ),
          ),
          const SizedBox(height: 18),
          GlassCard(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF18284A), Color(0xFF0B1426)],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    _typeChip(q.type),
                    const SizedBox(width: 6),
                    _difficultyChip(q.difficulty),
                  ],
                ),
                const SizedBox(height: 12),
                HyperlinkedText(q.prompt,
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        height: 1.35)),
              ],
            ),
          ),
          const SizedBox(height: 14),
          for (var i = 0; i < q.choices.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _ChoiceTile(
                label: q.choices[i],
                isSelected: _selected == i,
                isCorrect: _revealed && i == q.answer,
                isWrong:
                    _revealed && _selected == i && i != q.answer,
                onTap: _revealed
                    ? null
                    : () => setState(() {
                          _selected = i;
                        }),
              ),
            ),
          if (_revealed) ...[
            const SizedBox(height: 10),
            GlassCard(
              borderColor: (_selected == q.answer
                      ? AppColors.positive
                      : AppColors.negative)
                  .withValues(alpha: 0.5),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                          _selected == q.answer
                              ? Icons.check_circle
                              : Icons.cancel,
                          color: _selected == q.answer
                              ? AppColors.positive
                              : AppColors.negative,
                          size: 18),
                      const SizedBox(width: 8),
                      Text(
                          _selected == q.answer ? 'Correct' : 'Not quite',
                          style: TextStyle(
                              color: _selected == q.answer
                                  ? AppColors.positive
                                  : AppColors.negative,
                              fontWeight: FontWeight.w800,
                              fontSize: 13))
                    ],
                  ),
                  const SizedBox(height: 8),
                  HyperlinkedText(q.explanation,
                      style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 12.5,
                          height: 1.5)),
                ],
              ),
            ),
          ],
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: FilledButton(
                  style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      backgroundColor: AppColors.primary),
                  onPressed: _selected == null
                      ? null
                      : (_revealed ? _next : _reveal),
                  child: Text(_revealed
                      ? (_idx + 1 < _round.length ? 'Next' : 'Finish')
                      : 'Submit'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _typeChip(String t) {
    final map = {
      'mc': 'MULTIPLE CHOICE',
      'tf': 'TRUE / FALSE',
      'scenario': 'SCENARIO',
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.accent.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(6),
        border:
            Border.all(color: AppColors.accent.withValues(alpha: 0.45)),
      ),
      child: Text(map[t] ?? t.toUpperCase(),
          style: const TextStyle(
              color: AppColors.accent,
              fontSize: 9.5,
              letterSpacing: 1.0,
              fontWeight: FontWeight.w800)),
    );
  }

  Widget _difficultyChip(String d) {
    final color = d == 'easy'
        ? AppColors.positive
        : d == 'hard'
            ? AppColors.negative
            : AppColors.warning;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.45)),
      ),
      child: Text(d.toUpperCase(),
          style: TextStyle(
              color: color,
              fontSize: 9.5,
              letterSpacing: 1.0,
              fontWeight: FontWeight.w800)),
    );
  }

  void _reveal() {
    final q = _round[_idx];
    if (_selected == q.answer) {
      _correct += 1;
      XpService.instance.recordEvent('quiz_answer_correct', xpDelta: 10);
    } else {
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
              categoryKey: widget.categoryKey,
            )));
  }
}

class _ChoiceTile extends StatelessWidget {
  final String label;
  final bool isSelected;
  final bool isCorrect;
  final bool isWrong;
  final VoidCallback? onTap;
  const _ChoiceTile({
    required this.label,
    required this.isSelected,
    required this.isCorrect,
    required this.isWrong,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    Color border = AppColors.border;
    Color bg = AppColors.surface;
    if (isCorrect) {
      border = AppColors.positive;
      bg = AppColors.positive.withValues(alpha: 0.12);
    } else if (isWrong) {
      border = AppColors.negative;
      bg = AppColors.negative.withValues(alpha: 0.12);
    } else if (isSelected) {
      border = AppColors.accent;
      bg = AppColors.accent.withValues(alpha: 0.10);
    }
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding:
              const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          decoration: BoxDecoration(
            color: bg,
            border: Border.all(color: border, width: 1.2),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Text(label,
              style: TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 13,
                  fontWeight:
                      isSelected ? FontWeight.w800 : FontWeight.w600)),
        ),
      ),
    );
  }
}

class _ResultScreen extends StatelessWidget {
  final int correct;
  final int total;
  final String categoryKey;
  const _ResultScreen(
      {required this.correct,
      required this.total,
      required this.categoryKey});

  @override
  Widget build(BuildContext context) {
    final perfect = correct == total;
    final pct = (correct / total * 100).toStringAsFixed(0);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        title: const Text('Round complete',
            style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w800,
                fontSize: 16)),
      ),
      body: Padding(
        padding: const EdgeInsets.fromLTRB(20, 24, 20, 36),
        child: Column(
          children: [
            const SizedBox(height: 20),
            Container(
              width: 130,
              height: 130,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: perfect
                    ? AppColors.primaryGradient
                    : const LinearGradient(colors: [
                        Color(0xFF1A2A48),
                        Color(0xFF0B1426),
                      ]),
              ),
              child: Center(
                child: Text(
                  '$correct/$total',
                  style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                      fontSize: 36,
                      letterSpacing: -0.6),
                ),
              ),
            ).animate().scale(begin: const Offset(0.7, 0.7), end: const Offset(1, 1)),
            const SizedBox(height: 20),
            Text(perfect ? 'Perfect round!' : '$pct% correct',
                style: TextStyle(
                    color:
                        perfect ? AppColors.positive : AppColors.textPrimary,
                    fontSize: 22,
                    fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(
              perfect
                  ? 'Streak +1. Keep it going.'
                  : 'Review the explanations and try again.',
              style:
                  const TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
            const Spacer(),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      side:
                          const BorderSide(color: AppColors.border),
                      foregroundColor: AppColors.accent,
                    ),
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Back to Arena'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      backgroundColor: AppColors.primary,
                    ),
                    onPressed: () =>
                        Navigator.of(context).pushReplacement(
                      MaterialPageRoute(
                          builder: (_) =>
                              QuizPlayScreen(categoryKey: categoryKey)),
                    ),
                    child: const Text('Play again'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
