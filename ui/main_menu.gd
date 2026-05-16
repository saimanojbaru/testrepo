extends Control
## Main menu — entry point. Plays Chapter 1 fresh, resumes from save, or
## opens stats dashboard / journal.

const GAME_SCENE := "res://ui/game_scene.tscn"
const STATS_SCENE := "res://ui/stats_dashboard.tscn"
const JOURNAL_SCENE := "res://ui/journal.tscn"

var _start_chapter: String = "chapter_01_innocent_spark"


const ROOM_SCENE := "res://room/childhood_bedroom.tscn"


func _ready() -> void:
	$VBox/NewGame.pressed.connect(_on_new_game)
	$VBox/Continue.pressed.connect(_on_continue)
	$VBox/Stats.pressed.connect(_on_stats)
	$VBox/Journal.pressed.connect(_on_journal)
	$VBox/FreeTime.pressed.connect(_on_free_time)
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
