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

  List<QuizQuestion> questionsFor(String categoryKey) =>
      _questions.where((q) => q.category == categoryKey).toList();

  /// Builds a 5-question round, randomly drawn from the category.
  List<QuizQuestion> buildRound(String categoryKey, {int size = 5}) {
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
  const QuizCategory(
      {required this.key,
      required this.label,
      required this.icon,
      required this.description});
  factory QuizCategory.fromJson(Map<String, dynamic> j) => QuizCategory(
        key: j['key'] as String,
        label: j['label'] as String,
        icon: j['icon']?.toString() ?? 'book',
        description: j['description']?.toString() ?? '',
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
