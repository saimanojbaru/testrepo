import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:shared_preferences/shared_preferences.dart';

/// Backing state for the Audit Tools checklists.
///
/// Persists per-item completion + free-text notes + risk flags to
/// SharedPreferences. Singleton ChangeNotifier so any widget can
/// reflect updates immediately.
class ChecklistState extends ChangeNotifier {
  ChecklistState._();
  static final ChecklistState instance = ChecklistState._();

  static const _kPrefs = 'iip_checklist_state_v1';

  bool _loaded = false;
  late ChecklistCatalog _catalog;
  // {itemId: ItemState}
  final Map<String, ItemState> _items = {};

  ChecklistCatalog get catalog => _catalog;
  bool get loaded => _loaded;

  Future<void> load() async {
    if (_loaded) return;
    final text = await rootBundle.loadString('assets/data/checklists.json');
    _catalog = ChecklistCatalog.fromJson(
        jsonDecode(text) as Map<String, dynamic>);
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_kPrefs);
    if (raw != null) {
      try {
        final map = jsonDecode(raw) as Map<String, dynamic>;
        map.forEach((k, v) {
          _items[k] = ItemState.fromJson(Map<String, dynamic>.from(v as Map));
        });
      } catch (_) {}
    }
    _loaded = true;
    notifyListeners();
  }

  ItemState itemState(String itemId) =>
      _items[itemId] ?? const ItemState();

  Future<void> setCompleted(String itemId, bool value) async {
    final s = itemState(itemId);
    _items[itemId] = s.copyWith(completed: value);
    await _persist();
    notifyListeners();
  }

  Future<void> setFlagged(String itemId, bool value) async {
    final s = itemState(itemId);
    _items[itemId] = s.copyWith(flagged: value);
    await _persist();
    notifyListeners();
  }

  Future<void> setNote(String itemId, String note) async {
    final s = itemState(itemId);
    _items[itemId] = s.copyWith(note: note);
    await _persist();
    notifyListeners();
  }

  /// Aggregate stats for a template — completed/total/flagged.
  TemplateStats stats(ChecklistTemplate t) {
    var completed = 0;
    var flagged = 0;
    for (final i in t.items) {
      final s = itemState(i.id);
      if (s.completed) completed++;
      if (s.flagged) flagged++;
    }
    return TemplateStats(
        total: t.items.length, completed: completed, flagged: flagged);
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kPrefs,
        jsonEncode(_items.map((k, v) => MapEntry(k, v.toJson()))));
  }
}

class ItemState {
  final bool completed;
  final bool flagged;
  final String note;
  const ItemState({
    this.completed = false,
    this.flagged = false,
    this.note = '',
  });
  ItemState copyWith({bool? completed, bool? flagged, String? note}) =>
      ItemState(
        completed: completed ?? this.completed,
        flagged: flagged ?? this.flagged,
        note: note ?? this.note,
      );
  factory ItemState.fromJson(Map<String, dynamic> j) => ItemState(
        completed: j['completed'] == true,
        flagged: j['flagged'] == true,
        note: j['note']?.toString() ?? '',
      );
  Map<String, dynamic> toJson() =>
      {'completed': completed, 'flagged': flagged, 'note': note};
}

class TemplateStats {
  final int total;
  final int completed;
  final int flagged;
  const TemplateStats({
    required this.total,
    required this.completed,
    required this.flagged,
  });
  double get progress => total == 0 ? 0 : completed / total;
}

class ChecklistCatalog {
  final List<PhaseDef> phases;
  final List<ChecklistTemplate> templates;
  const ChecklistCatalog({required this.phases, required this.templates});
  factory ChecklistCatalog.fromJson(Map<String, dynamic> j) {
    return ChecklistCatalog(
      phases: ((j['phases'] as List?) ?? [])
          .map((e) => PhaseDef.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      templates: ((j['templates'] as List?) ?? [])
          .map((e) =>
              ChecklistTemplate.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
    );
  }
}

class PhaseDef {
  final String key;
  final String label;
  final String icon;
  const PhaseDef({required this.key, required this.label, required this.icon});
  factory PhaseDef.fromJson(Map<String, dynamic> j) => PhaseDef(
        key: j['key'] as String,
        label: j['label'] as String,
        icon: j['icon'] as String? ?? '',
      );
}

class ChecklistTemplate {
  final String id;
  final String name;
  final String subtitle;
  final List<String> tags;
  final List<String> relevantCarriers;
  final List<GuidanceLink> guidance;
  final List<ChecklistItem> items;

  const ChecklistTemplate({
    required this.id,
    required this.name,
    required this.subtitle,
    required this.tags,
    required this.relevantCarriers,
    required this.guidance,
    required this.items,
  });

  factory ChecklistTemplate.fromJson(Map<String, dynamic> j) =>
      ChecklistTemplate(
        id: j['id'] as String,
        name: j['name'] as String,
        subtitle: j['subtitle']?.toString() ?? '',
        tags: ((j['tags'] as List?) ?? [])
            .map((e) => e.toString())
            .toList(),
        relevantCarriers: ((j['relevant_carriers'] as List?) ?? [])
            .map((e) => e.toString())
            .toList(),
        guidance: ((j['guidance'] as List?) ?? [])
            .map((e) =>
                GuidanceLink.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        items: ((j['items'] as List?) ?? [])
            .map((e) =>
                ChecklistItem.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );

  List<ChecklistItem> itemsForPhase(String phaseKey) =>
      items.where((i) => i.phase == phaseKey).toList();
}

class GuidanceLink {
  final String label;
  final String framework;
  final String id;
  const GuidanceLink(
      {required this.label, required this.framework, required this.id});
  factory GuidanceLink.fromJson(Map<String, dynamic> j) => GuidanceLink(
        label: j['label'] as String,
        framework: j['framework'] as String,
        id: j['id'] as String,
      );
}

class ChecklistItem {
  final String id;
  final String phase;
  final String task;
  final String guidance;
  const ChecklistItem({
    required this.id,
    required this.phase,
    required this.task,
    required this.guidance,
  });
  factory ChecklistItem.fromJson(Map<String, dynamic> j) => ChecklistItem(
        id: j['id'] as String,
        phase: j['phase'] as String,
        task: j['task'] as String,
        guidance: j['guidance']?.toString() ?? '',
      );
}
