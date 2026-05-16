extends Node
## StatEngine — single source of truth for player stats.
##
## Loads stat definitions from data/stats/stats.json at boot.
## Exposes get_stat / modify / tick_day. Emits stat_changed and
## stat_threshold_crossed signals. Holds no narrative knowledge.

signal stat_changed(id: String, old_value: float, new_value: float, source: String)
signal stat_threshold_crossed(id: String, threshold: float, direction: String)

const STATS_FILE := "res://data/stats/stats.json"
const FORMULAS_FILE := "res://data/stats/formulas.json"
const CONSEQUENCES_FILE := "res://data/stats/consequences.json"

var _definitions: Dictionary = {}
var _values: Dictionary = {}
var _formulas: Array = []
var _consequences: Array = []
var _consequence_streaks: Dictionary = {}
var _current_day: int = 0


func _ready() -> void:
	_load_definitions()
	_load_formulas()
	_load_consequences()
	_reset_to_defaults()


func _load_definitions() -> void:
	var raw: Variant = _read_json(STATS_FILE)
	if typeof(raw) != TYPE_DICTIONARY:
		push_error("StatEngine: stats.json is malformed")
		return
	_definitions = raw


func _load_formulas() -> void:
	var raw: Variant = _read_json(FORMULAS_FILE)
	_formulas = raw if typeof(raw) == TYPE_ARRAY else []


func _load_consequences() -> void:
	var raw: Variant = _read_json(CONSEQUENCES_FILE)
	_consequences = raw if typeof(raw) == TYPE_ARRAY else []


func _read_json(path: String) -> Variant:
	if not FileAccess.file_exists(path):
		push_warning("StatEngine: missing %s" % path)
		return null
	var f := FileAccess.open(path, FileAccess.READ)
	var text: String = f.get_as_text()
	f.close()
	return JSON.parse_string(text)


func _reset_to_defaults() -> void:
	_values.clear()
	for id in _definitions.keys():
		var def: Dictionary = _definitions[id]
		_values[id] = float(def.get("start", 0))


func get_stat(id: String) -> float:
	return float(_values.get(id, 0.0))


func has_stat(id: String) -> bool:
	return _definitions.has(id)


func get_all() -> Dictionary:
	return _values.duplicate(true)


func modify(id: String, delta: float, source: String = "unknown") -> void:
	if not _definitions.has(id):
		push_warning("StatEngine: unknown stat '%s' from source '%s'" % [id, source])
		return
	var def: Dictionary = _definitions[id]
	var old: float = float(_values.get(id, 0.0))
	var new_val: float = clamp(
		old + delta,
		float(def.get("min", -INF)),
		float(def.get("max", INF))
	)
	if is_equal_approx(old, new_val):
		return
	_values[id] = new_val
	stat_changed.emit(id, old, new_val, source)


func set_stat(id: String, value: float, source: String = "unknown") -> void:
	if not _definitions.has(id):
		push_warning("StatEngine: unknown stat '%s' from source '%s'" % [id, source])
		return
	modify(id, value - get_stat(id), source)


func tick_day() -> void:
	_current_day += 1
	for id in _definitions.keys():
		var def: Dictionary = _definitions[id]
		var decay: float = float(def.get("decay_per_day", 0.0))
		if not is_zero_approx(decay):
			modify(id, decay, "daily_decay")
	_evaluate_consequences()


func _evaluate_consequences() -> void:
	for rule in _consequences:
		var key: String = String(rule.get("fire_event", ""))
		if key.is_empty():
			continue
		var condition_met := _evaluate_condition(rule.get("if", ""))
		if condition_met:
			_consequence_streaks[key] = int(_consequence_streaks.get(key, 0)) + 1
		else:
			_consequence_streaks[key] = 0
		var streak_required: int = int(rule.get("streak_days", 1))
		if int(_consequence_streaks.get(key, 0)) >= streak_required:
			stat_threshold_crossed.emit(key, float(rule.get("weight", 1.0)), "rising")
			_consequence_streaks[key] = 0


func _evaluate_condition(expr: String) -> bool:
	# Minimal expression evaluator. Supports comparisons and `and` only.
	# Real implementation should use Expression class for safe eval.
	if expr.is_empty():
		return false
	var e := Expression.new()
	var err: int = e.parse(expr, _values.keys())
	if err != OK:
		return false
	var result: Variant = e.execute(_values.values(), self, false)
	return bool(result)


func serialize() -> Dictionary:
	return {
		"values": _values.duplicate(true),
		"day": _current_day,
		"streaks": _consequence_streaks.duplicate(true),
	}


func deserialize(data: Dictionary) -> void:
	_reset_to_defaults()
	for id in data.get("values", {}).keys():
		if _definitions.has(id):
			_values[id] = float(data["values"][id])
	_current_day = int(data.get("day", 0))
	_consequence_streaks = data.get("streaks", {}).duplicate(true)
