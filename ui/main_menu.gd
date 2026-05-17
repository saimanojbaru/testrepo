extends Control
## Main menu — entry point. Plays Chapter 1 fresh, resumes from save, opens
## stats / journal / 3D room, or fetches an OTA content patch.

const GAME_SCENE := "res://ui/game_scene.tscn"
const STATS_SCENE := "res://ui/stats_dashboard.tscn"
const JOURNAL_SCENE := "res://ui/journal.tscn"
const ROOM_SCENE := "res://room/childhood_bedroom.tscn"
const MENU_TOAST := preload("res://ui/menu_toast.tscn")

var _start_chapter: String = "chapter_01_innocent_spark"
var _check_btn: Button = null


func _ready() -> void:
	$VBox/NewGame.pressed.connect(_on_new_game)
	$VBox/Continue.pressed.connect(_on_continue)
	$VBox/Stats.pressed.connect(_on_stats)
	$VBox/Journal.pressed.connect(_on_journal)
	$VBox/FreeTime.pressed.connect(_on_free_time)
	_check_btn = $VBox/CheckUpdates
	_check_btn.pressed.connect(_on_check_updates)
	$VBox/Continue.disabled = not SaveManager.has_save(0)
	ButtonFX.attach_all(self)


func _on_free_time() -> void:
	get_tree().change_scene_to_file(ROOM_SCENE)


func _on_new_game() -> void:
	SaveManager.set_active_slot(0)
	get_tree().change_scene_to_file(GAME_SCENE)


func set_start_chapter(chapter_id: String) -> void:
	_start_chapter = chapter_id


func _on_continue() -> void:
	SaveManager.load_slot(0)
	get_tree().change_scene_to_file(GAME_SCENE)


func _on_stats() -> void:
	get_tree().change_scene_to_file(STATS_SCENE)


func _on_journal() -> void:
	get_tree().change_scene_to_file(JOURNAL_SCENE)


func _on_check_updates() -> void:
	if _check_btn == null:
		return
	_check_btn.disabled = true
	_check_btn.text = "  ›  Checking…"
	OTAManager.update_check_completed.connect(_on_update_done, CONNECT_ONE_SHOT)
	OTAManager.check_for_updates()


func _on_update_done(applied: bool, message: String) -> void:
	if _check_btn:
		_check_btn.disabled = false
		_check_btn.text = "  ›  Check for Updates"
	var toast: Control = MENU_TOAST.instantiate()
	add_child(toast)
	var col := Color(0.50, 0.85, 0.60, 1.0) if applied else Color(0.98, 0.94, 0.86, 1.0)
	toast.show_message(message, 3.6, col)
