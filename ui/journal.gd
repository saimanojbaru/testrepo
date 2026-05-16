extends Control
## Journal — Memory Echoes. Loads prompts from data/memory_echoes/prompts.json
## and stores entries in user://journal.json keyed by trigger.

const PROMPTS_FILE := "res://data/memory_echoes/prompts.json"
const ENTRIES_FILE := "user://journal.json"

var _prompts: Dictionary = {}
var _entries: Dictionary = {}


func _ready() -> void:
	$Back.pressed.connect(func(): get_tree().change_scene_to_file("res://ui/main_menu.tscn"))
	$Save.pressed.connect(_on_save)
	_load_prompts()
	_load_entries()
	_pick_prompt()


func _load_prompts() -> void:
	if not FileAccess.file_exists(PROMPTS_FILE):
		return
	var f := FileAccess.open(PROMPTS_FILE, FileAccess.READ)
	_prompts = JSON.parse_string(f.get_as_text())
	f.close()


func _load_entries() -> void:
	if not FileAccess.file_exists(ENTRIES_FILE):
		_entries = {}
		return
	var f := FileAccess.open(ENTRIES_FILE, FileAccess.READ)
	var parsed: Variant = JSON.parse_string(f.get_as_text())
	_entries = parsed if typeof(parsed) == TYPE_DICTIONARY else {}
	f.close()


func _save_entries() -> void:
	var f := FileAccess.open(ENTRIES_FILE, FileAccess.WRITE)
	f.store_string(JSON.stringify(_entries, "  "))
	f.close()


func _pick_prompt() -> void:
	# Choose the first prompt the player hasn't answered yet.
	for key in _prompts.keys():
		if not _entries.has(key):
			_show(key, _prompts[key])
			return
	$Prompt.text = "You've answered every prompt. New ones unlock as your story unfolds."
	$Entry.editable = false
	$Save.disabled = true


func _show(key: String, prompt: Dictionary) -> void:
	$Prompt.text = String(prompt.get("prompt", ""))
	$Entry.text = ""
	$Entry.editable = true
	$Save.disabled = false
	$Save.set_meta("key", key)


func _on_save() -> void:
	var key: String = String($Save.get_meta("key", ""))
	if key.is_empty():
		return
	var entry_text: String = $Entry.text
	if entry_text.strip_edges().length() < int(_prompts[key].get("min_chars", 10)):
		$Status.text = "A few more words..."
		return
	_entries[key] = {
		"text": entry_text,
		"saved_at": Time.get_unix_time_from_system(),
	}
	_save_entries()
	StatEngine.modify("wisdom", 1.5, "journal_entry")
	$Status.text = "Saved. +1.5 wisdom."
	await get_tree().create_timer(1.2).timeout
	_pick_prompt()
	$Status.text = ""
