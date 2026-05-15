import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:shared_preferences/shared_preferences.dart';

/// Quiz state — categories, questions, high scores, win streaks.
class QuizState extends ChangeNotifier {
  QuizState._();
  static final QuizState instance = QuizState._();

  static const _kHighScores = 'iip_quiz_highscores';
  static const _kStreaks = 'iip_quiz_streaks';

  bool _loaded = false;
  List<QuizCategory> _categories = const [];
  List<QuizQuestion> _questions = const [];
  Map<String, int> _highScores = {};   // category → best score (correct count)
  Map<String, int> _streaks = {};      // category → current streak

  bool get loaded => _loaded;
  List<QuizCategory> get categories => _categories;

  Future<void> load() async {
    if (_loaded) return;
    final text = await rootBundle.loadString('assets/data/quiz.json');
    final body = jsonDecode(text) as Map<String, dynamic>;
    _categories = ((body['categories'] as List?) ?? [])
        .map((e) =>
            QuizCategory.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    _questions = ((body['questions'] as List?) ?? [])
        .map((e) =>
            QuizQuestion.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
    final prefs = await SharedPreferences.getInstance();
    _highScores = _decodeMap(prefs.getString(_kHighScores));
    _streaks = _decodeMap(prefs.getString(_kStreaks));
    _loaded = true;
    notifyListeners();
  }

  Map<String, int> _decodeMap(String? raw) {
    if (raw == null) return {};
    try {
      final m = jsonDecode(raw) as Map<String, dynamic>;
      return m.map((k, v) => MapEntry(k, (v as num).toInt()));
    } catch (_) {
      return {};
    }
  }

  List<QuizQuestion> questionsFor(String categoryKey) {
    // Zumble Mode: adaptive mixed-difficulty draw from all topics.
    if (categoryKey == 'zumble') return _questions.toList();
    return _questions.where((q) => q.category == categoryKey).toList();
  }

  /// Builds a 5-question round, randomly drawn from the category.
  /// For Zumble Mode the draw blends easy / medium / hard.
  List<QuizQuestion> buildRound(String categoryKey, {int size = 5}) {
    if (categoryKey == 'zumble') {
      final easy = _questions
          .where((q) => q.difficulty == 'easy')
          .toList()
        ..shuffle(Random());
      final medium = _questions
          .where((q) => q.difficulty == 'medium')
          .toList()
        ..shuffle(Random());
      final hard = _questions
          .where((q) => q.difficulty == 'hard')
          .toList()
        ..shuffle(Random());
      final mix = <QuizQuestion>[];
      void take(List<QuizQuestion> from, int n) {
        for (var i = 0; i < n && i < from.length; i++) {
          mix.add(from[i]);
        }
      }
      // Adaptive: 2 easy, 2 medium, 1 hard (fallback to whatever's available).
      take(easy, 2);
      take(medium, 2);
      take(hard, 1);
      // Top up if any tier was short.
      while (mix.length < size) {
        final all = _questions.toList()..shuffle(Random());
        for (final q in all) {
          if (mix.length >= size) break;
          if (!mix.contains(q)) mix.add(q);
        }
      }
      return mix.take(size).toList();
    }
    final pool = questionsFor(categoryKey).toList()..shuffle(Random());
    return pool.take(size).toList();
  }

  int highScore(String key) => _highScores[key] ?? 0;
  int streak(String key) => _streaks[key] ?? 0;

  Future<void> recordResult({
    required String categoryKey,
    required int correct,
    required int total,
  }) async {
    if (correct >= total) {
      _streaks[categoryKey] = (_streaks[categoryKey] ?? 0) + 1;
    } else {
      _streaks[categoryKey] = 0;
    }
    if (correct > (_highScores[categoryKey] ?? 0)) {
      _highScores[categoryKey] = correct;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kHighScores, jsonEncode(_highScores));
    await prefs.setString(_kStreaks, jsonEncode(_streaks));
    notifyListeners();
  }
}

class QuizCategory {
  final String key;
  final String label;
  final String icon;
  final String description;
  final String tier; // Beginner / Intermediate / Advanced / Expert / Adaptive
  const QuizCategory({
    required this.key,
    required this.label,
    required this.icon,
    required this.description,
    required this.tier,
  });
  factory QuizCategory.fromJson(Map<String, dynamic> j) => QuizCategory(
        key: j['key'] as String,
        label: j['label'] as String,
        icon: j['icon']?.toString() ?? 'book',
        description: j['description']?.toString() ?? '',
        tier: j['tier']?.toString() ?? 'Beginner',
      );
}

class QuizQuestion {
  final String id;
  final String category;
  final String type; // mc, tf, scenario
  final String prompt;
  final List<String> choices;
  final int answer;
  final String explanation;
  final String difficulty;

  const QuizQuestion({
    required this.id,
    required this.category,
    required this.type,
    required this.prompt,
    required this.choices,
    required this.answer,
    required this.explanation,
    required this.difficulty,
  });

  factory QuizQuestion.fromJson(Map<String, dynamic> j) => QuizQuestion(
        id: j['id'] as String,
        category: j['category'] as String,
        type: j['type'] as String,
        prompt: j['prompt'] as String,
        choices: ((j['choices'] as List?) ?? [])
            .map((e) => e.toString())
            .toList(),
        answer: (j['answer'] as num).toInt(),
        explanation: j['explanation']?.toString() ?? '',
        difficulty: j['difficulty']?.toString() ?? 'medium',
      );
}
