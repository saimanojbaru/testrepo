extends Node
## InkBridge / NarrativeRunner.
##
## Long-term: this wraps the `inkgd` addon and feeds it Ink-compiled JSON
## stories. Story files are authored as .ink and compiled with inklecate.
##
## MVP fallback (used until inkgd is installed): a minimal JSON narrative
## reader that supports lines, choices, stat ops, and knot jumps. The schema
## is documented in chapters/chapter_01_innocent_spark/story.fallback.json.
## Both the .ink source and the fallback JSON should stay in sync — the .ink
## file is authoritative.

signal dialogue_line(speaker: String, text: String, meta: Dictionary)
signal choices_offered(choices: Array)
signal story_finished()
signal chapter_finished(chapter_id: String, next_chapter_id: String)
signal knot_entered(knot: String)
signal mood_requested(mood_id: String, duration: float)

const STORY_FALLBACK_DIR := "res://chapters"
const INKGD_RUNTIME_PATH := "res://addons/inkgd/runtime/ink_player.gd"

var _story: Dictionary = {}
var _current_knot: String = ""
var _line_index: int = 0
var _visited_knots: Array = []
var _waiting_for_choice: bool = false
var _chapter_finished_fired: bool = false
var _ink_player: Object = null  # populated only if inkgd is present


func _ready() -> void:
	_try_load_inkgd()


func _try_load_inkgd() -> void:
	if ResourceLoader.exists(INKGD_RUNTIME_PATH):
		var script: Script = load(INKGD_RUNTIME_PATH)
		if script != null:
			_ink_player = script.new()
			add_child(_ink_player)
			print("InkBridge: inkgd runtime loaded")
		return
	print("InkBridge: inkgd not installed — using JSON fallback reader")


func load_story(chapter_id: String) -> void:
	if _ink_player != null:
		_load_with_inkgd(chapter_id)
	else:
		_load_with_fallback(chapter_id)


func _load_with_inkgd(chapter_id: String) -> void:
	# inkgd integration. The compiled .json (output of `inklecate` on story.ink)
	# is expected at chapters/<id>/story.json. The exact API depends on the
	# inkgd version; this is a placeholder that hands off to the player.
	var compiled := "%s/%s/story.json" % [STORY_FALLBACK_DIR, chapter_id]
	if _ink_player.has_method("create_story"):
		_ink_player.call("create_story", compiled)


func _load_with_fallback(chapter_id: String) -> void:
	var path: String = "%s/%s/story.fallback.json" % [STORY_FALLBACK_DIR, chapter_id]
	if not FileAccess.file_exists(path):
		push_error("InkBridge: missing fallback story %s" % path)
		return
	var f := FileAccess.open(path, FileAccess.READ)
	var parsed: Variant = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) != TYPE_DICTIONARY:
		push_error("InkBridge: malformed story %s" % path)
		return
	_story = parsed
	_visited_knots.clear()
	_waiting_for_choice = false
	_chapter_finished_fired = false
	_jump_to_knot(String(_story.get("start", "")))


func _jump_to_knot(knot: String) -> void:
	if not _story.get("knots", {}).has(knot):
		push_error("InkBridge: unknown knot '%s'" % knot)
		story_finished.emit()
		return
	_current_knot = knot
	_line_index = 0
	if not _visited_knots.has(knot):
		_visited_knots.append(knot)
	knot_entered.emit(knot)
	_advance()


func continue_story() -> void:
	if _waiting_for_choice:
		return
	_advance()


func choose(index: int) -> void:
	if not _waiting_for_choice:
		return
	var knot_data: Dictionary = _story["knots"][_current_knot]
	var choices: Array = knot_data.get("choices", [])
	if index < 0 or index >= choices.size():
		return
	var choice: Dictionary = choices[index]
	_apply_effects(choice.get("effects", []))
	_waiting_for_choice = false
	_jump_to_knot(String(choice.get("goto", "")))


func _advance() -> void:
	var knot_data: Dictionary = _story["knots"][_current_knot]
	var lines: Array = knot_data.get("lines", [])
	if _line_index >= lines.size():
		_present_choices_or_goto(knot_data)
		return
	var line: Dictionary = lines[_line_index]
	_line_index += 1
	if line.has("mood"):
		mood_requested.emit(
			String(line["mood"]),
			float(line.get("mood_fade", 4.0))
		)
	if line.has("effects"):
		_apply_effects(line["effects"])
	dialogue_line.emit(
		String(line.get("speaker", "")),
		String(line.get("text", "")),
		line.duplicate(true)
	)


func _present_choices_or_goto(knot_data: Dictionary) -> void:
	var choices: Array = knot_data.get("choices", [])
	if not choices.is_empty():
		_waiting_for_choice = true
		choices_offered.emit(choices.map(func(c): return String(c.get("text", ""))))
		return
	# Stat-conditional branching (multiple endings).
	if knot_data.has("branch_on_stats"):
		for rule in knot_data["branch_on_stats"]:
			if _evaluate_stat_condition(String(rule.get("when", ""))):
				_jump_to_knot(String(rule.get("goto", "")))
				return
	if knot_data.has("goto"):
		_jump_to_knot(String(knot_data["goto"]))
		return
	var next_chap: String = String(_story.get("next_chapter", ""))
	if not next_chap.is_empty() and next_chap != "null":
		if _chapter_finished_fired:
			return
		_chapter_finished_fired = true
		_waiting_for_choice = true   # block further continue_story() until reset
		chapter_finished.emit(String(_story.get("chapter_id", "")), next_chap)
	else:
		story_finished.emit()


func _evaluate_stat_condition(expr: String) -> bool:
	if expr.is_empty():
		return false
	var stats := StatEngine.get_all()
	var keys: Array = stats.keys()
	var values: Array = []
	for k in keys:
		values.append(stats[k])
	var e := Expression.new()
	if e.parse(expr, keys) != OK:
		return false
	var result: Variant = e.execute(values, null, false)
	return bool(result)


func load_next_chapter() -> void:
	var next_chap: String = String(_story.get("next_chapter", ""))
	if not next_chap.is_empty() and next_chap != "null":
		load_story(next_chap)


func _apply_effects(effects: Array) -> void:
	for effect in effects:
		var stat: String = String(effect.get("stat", ""))
		var delta: float = float(effect.get("delta", 0.0))
		var src: String = String(effect.get("source", "ink"))
		if not stat.is_empty():
			StatEngine.modify(stat, delta, src)


func serialize() -> Dictionary:
	return {
		"current_knot": _current_knot,
		"line_index": _line_index,
		"visited": _visited_knots.duplicate(true),
	}


func deserialize(data: Dictionary) -> void:
	_current_knot = String(data.get("current_knot", ""))
	_line_index = int(data.get("line_index", 0))
	_visited_knots = data.get("visited", []).duplicate(true)
