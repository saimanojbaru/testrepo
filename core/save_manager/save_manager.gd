extends Node
## SaveManager — persists game state.
##
## Phase 1 (MVP): JSON snapshot only at user://saves/slot_N/snapshot.json.
## Phase 2: append SQLite history log via godot-sqlite GDExtension, plus
## Google Play Games Services Saved Games cloud sync.

signal save_completed(slot: int)
signal load_completed(slot: int, success: bool)

const SAVE_DIR := "user://saves"
const SNAPSHOT_FILE := "snapshot.json"
const CURRENT_SCHEMA_VERSION := 1

var _active_slot: int = 0
var _dirty: bool = false
var _autosave_pending: bool = false


func _ready() -> void:
	_ensure_save_dir(_active_slot)
	StatEngine.stat_changed.connect(_on_stat_changed)


func _ensure_save_dir(slot: int) -> void:
	var path := "%s/slot_%d" % [SAVE_DIR, slot]
	if not DirAccess.dir_exists_absolute(ProjectSettings.globalize_path(path)):
		DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(path))


func set_active_slot(slot: int) -> void:
	_active_slot = slot
	_ensure_save_dir(slot)


func save_now() -> void:
	_ensure_save_dir(_active_slot)
	var payload := {
		"schema": CURRENT_SCHEMA_VERSION,
		"saved_at": Time.get_unix_time_from_system(),
		"stats": StatEngine.serialize(),
		"narrative": InkBridge.serialize(),
		"mood": {
			"current": MoodController.current_mood_id(),
		},
	}
	var path := _snapshot_path(_active_slot)
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f == null:
		push_error("SaveManager: cannot write %s" % path)
		return
	f.store_string(JSON.stringify(payload, "  "))
	f.close()
	_dirty = false
	save_completed.emit(_active_slot)


func load_slot(slot: int) -> bool:
	set_active_slot(slot)
	var path := _snapshot_path(slot)
	if not FileAccess.file_exists(path):
		load_completed.emit(slot, false)
		return false
	var f := FileAccess.open(path, FileAccess.READ)
	var parsed: Variant = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) != TYPE_DICTIONARY:
		load_completed.emit(slot, false)
		return false
	StatEngine.deserialize(parsed.get("stats", {}))
	InkBridge.deserialize(parsed.get("narrative", {}))
	var mood_id: String = parsed.get("mood", {}).get("current", "ch1_innocent")
	MoodController.transition_to(mood_id, 0.5)
	load_completed.emit(slot, true)
	return true


func has_save(slot: int) -> bool:
	return FileAccess.file_exists(_snapshot_path(slot))


func _snapshot_path(slot: int) -> String:
	return "%s/slot_%d/%s" % [SAVE_DIR, slot, SNAPSHOT_FILE]


func _on_stat_changed(_id: String, _old: float, _new: float, _src: String) -> void:
	_dirty = true
	if not _autosave_pending:
		_autosave_pending = true
		get_tree().create_timer(2.0).timeout.connect(_flush_autosave)


func _flush_autosave() -> void:
	_autosave_pending = false
	if _dirty:
		save_now()


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_APPLICATION_PAUSED:
		if _dirty:
			save_now()
