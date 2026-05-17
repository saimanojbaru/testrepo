extends Control
## GameScene — primary playback. Top-anchored dialogue panel, cross-faded
## scene backgrounds, BBCode-aware typewriter, floating stat-delta toasts,
## drag-and-drop tactile micro-interactions.

@export var chapter_id: String = "chapter_01_innocent_spark"

const TYPEWRITER_CHARS_PER_SEC := 48.0
const BG_FADE_SEC := 0.9
const TRANSITION_FADE_SEC := 2.5
const DELTA_STACK_RESET_SEC := 0.85
const DELTA_STACK_STEP := 64.0

const CHOICE_BUTTON := preload("res://ui/choice_button.tscn")
const STAT_DELTA_TOAST := preload("res://ui/stat_delta_toast.tscn")
const BUDGET_ALLOCATOR := preload("res://minigames/budget_allocator/budget_allocator.tscn")
const MEETING_SURVIVAL := preload("res://minigames/meeting_survival/meeting_survival.tscn")
const EMI_SIMULATOR := preload("res://minigames/emi_simulator/emi_simulator.tscn")

const TACTILE_SCENES := {
	"pack_tiffin":          "res://minigames/tactile/pack_tiffin/pack_tiffin.tscn",
}

const SILENT_DELTA_SOURCES := {
	"daily_decay": true,
	"budget_allocator": true,
	"emi_simulator": true,
	"meeting_survival": true,
}

@onready var _bg_a: TextureRect           = $BackgroundA
@onready var _bg_b: TextureRect           = $BackgroundB
@onready var _fallback_bg: ColorRect      = $FallbackBackground
@onready var _dialogue: PanelContainer    = $DialoguePanel
@onready var _body: RichTextLabel         = $DialoguePanel/VBoxContainer/Body
@onready var _continue_hint: Label        = $DialoguePanel/VBoxContainer/ContinueHint
@onready var _speaker_label: Label        = $DialoguePanel/VBoxContainer/SpeakerLabel
@onready var _speaker_divider: HSeparator = $DialoguePanel/VBoxContainer/SpeakerDivider
@onready var _choices_root: VBoxContainer = $ChoicesContainer
@onready var _delta_overlay: Control      = $DeltaOverlay
@onready var _metric_dashboard: Control   = $HUDLayer/MetricDashboard

var _visible_chars_target: int = 0
var _visible_chars_t: float = 0.0
var _typewriter_done: bool = true
var _minigame_active: bool = false
var _transitioning: bool = false
var _current_bg_path: String = ""
var _bg_tween: Tween = null
var _delta_stack_y: float = 0.0
var _delta_stack_reset_timer: float = 0.0


func _ready() -> void:
	_choices_root.visible = false
	_continue_hint.visible = false
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_fallback_bg.visible = true

	InkBridge.dialogue_line.connect(_on_dialogue_line)
	InkBridge.choices_offered.connect(_on_choices_offered)
	InkBridge.mood_requested.connect(_on_mood_requested)
	InkBridge.knot_entered.connect(_on_knot_entered)
	InkBridge.story_finished.connect(_on_story_finished)
	InkBridge.chapter_finished.connect(_on_chapter_finished)
	StatEngine.stat_changed.connect(_on_stat_changed)

	_apply_safe_area()
	set_process(true)
	call_deferred("_start_story")


func _apply_safe_area() -> void:
	if not OS.has_feature("mobile"):
		return
	var safe := DisplayServer.get_display_safe_area()
	var screen := DisplayServer.screen_get_size()
	var top_pad: float = max(0.0, float(safe.position.y))
	var bottom_pad: float = max(0.0, float(screen.y - (safe.position.y + safe.size.y)))
	if _metric_dashboard:
		_metric_dashboard.offset_top    += top_pad
		_metric_dashboard.offset_bottom += top_pad
	if _dialogue:
		_dialogue.offset_bottom -= bottom_pad


func _start_story() -> void:
	InkBridge.load_story(chapter_id)


func _unhandled_input(event: InputEvent) -> void:
	if _choices_root.visible or _minigame_active or _transitioning:
		return
	var consume := false
	if event is InputEventScreenTouch and event.pressed:
		consume = true
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		consume = true
	elif event is InputEventKey and event.pressed and not event.echo:
		consume = true
	if consume:
		_advance_or_continue()
		get_viewport().set_input_as_handled()


func _advance_or_continue() -> void:
	if not _typewriter_done:
		_typewriter_done = true
		_body.visible_characters = _visible_chars_target
		_continue_hint.visible = true
		return
	InkBridge.continue_story()


func _process(delta: float) -> void:
	if _delta_stack_reset_timer > 0.0:
		_delta_stack_reset_timer = max(0.0, _delta_stack_reset_timer - delta)
		if _delta_stack_reset_timer == 0.0:
			_delta_stack_y = 0.0
	if _typewriter_done:
		return
	if _visible_chars_target <= 0:
		return
	_visible_chars_t += delta * TYPEWRITER_CHARS_PER_SEC
	var shown: int = min(int(_visible_chars_t), _visible_chars_target)
	_body.visible_characters = shown
	if shown >= _visible_chars_target:
		_typewriter_done = true
		_continue_hint.visible = true


func _on_dialogue_line(speaker: String, text: String, meta: Dictionary) -> void:
	# Background swap (per-line override beats knot).
	if meta.has("background"):
		_set_background(String(meta["background"]))

	# Speaker header.
	if speaker.is_empty():
		_speaker_label.visible = false
		_speaker_divider.visible = false
	else:
		_speaker_label.text = speaker
		_speaker_label.visible = true
		_speaker_divider.visible = true

	_body.text = text
	_body.visible_characters = 0
	_visible_chars_t = 0.0
	_typewriter_done = false
	_continue_hint.visible = false

	# BBCode parsing needs a frame before character count is correct.
	await get_tree().process_frame
	_visible_chars_target = _body.get_total_character_count()
	if _visible_chars_target <= 0:
		# Empty / whitespace-only line — skip typewriter, allow immediate continue.
		_typewriter_done = true
		_continue_hint.visible = true

	# Hide HUD during reflective / climactic beats; show otherwise.
	var hide_hud := bool(meta.get("cinematic", false)) or meta.has("memory_echo")
	if _metric_dashboard and _metric_dashboard.has_method("set_hud_visible"):
		_metric_dashboard.set_hud_visible(not hide_hud)

	if meta.has("minigame"):
		_launch_minigame(String(meta["minigame"]))
	if meta.has("tactile"):
		_launch_tactile(String(meta["tactile"]))
	if meta.has("memory_echo"):
		_offer_memory_echo(String(meta["memory_echo"]))


func _on_choices_offered(choices: Array) -> void:
	for c in _choices_root.get_children():
		c.queue_free()
	for i in choices.size():
		var btn := CHOICE_BUTTON.instantiate()
		btn.set_choice_text(String(choices[i]))
		var idx: int = i
		btn.pressed.connect(func(): _on_choice_pressed(idx))
		_choices_root.add_child(btn)
	_choices_root.visible = true
	_continue_hint.visible = false
	_dialogue.visible = false
	# Container is anchored bottom + grow_vertical=BEGIN, so it auto-sizes
	# upward from the screen bottom to fit its children.


func _on_choice_pressed(index: int) -> void:
	_choices_root.visible = false
	_dialogue.visible = true
	InkBridge.choose(index)


func _on_mood_requested(mood_id: String, duration: float) -> void:
	MoodController.transition_to(mood_id, duration)


func _on_knot_entered(knot: String) -> void:
	print("[Story] knot: ", knot)


func _on_story_finished() -> void:
	if _transitioning:
		return
	_transitioning = true
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_body.text = "[i]Your story is complete.\nTap to return to the main menu.[/i]"
	await get_tree().process_frame
	_visible_chars_target = _body.get_total_character_count()
	_body.visible_characters = _visible_chars_target
	_typewriter_done = true
	_continue_hint.visible = true
	_choices_root.visible = false
	await get_tree().create_timer(2.8).timeout
	get_tree().change_scene_to_file("res://ui/main_menu.tscn")


func _on_chapter_finished(_finished_id: String, next_chapter_id: String) -> void:
	if _transitioning:
		return
	_transitioning = true
	chapter_id = next_chapter_id
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_body.text = "[i]Chapter complete.\nLoading next chapter...[/i]"
	await get_tree().process_frame
	_visible_chars_target = _body.get_total_character_count()
	_body.visible_characters = _visible_chars_target
	_typewriter_done = true
	_continue_hint.visible = false
	await get_tree().create_timer(TRANSITION_FADE_SEC).timeout
	InkBridge.load_story(next_chapter_id)
	SaveManager.save_now()
	await get_tree().process_frame
	_transitioning = false


func _set_background(filename: String) -> void:
	if filename == _current_bg_path:
		return
	var full_path: String = "res://chapters/%s/backgrounds/%s" % [chapter_id, filename]
	if not ResourceLoader.exists(full_path):
		push_warning("GameScene: missing background %s" % full_path)
		return
	var tex: Texture2D = load(full_path)
	if tex == null:
		return
	_current_bg_path = filename
	_fallback_bg.visible = false
	_bg_b.texture = tex
	_bg_b.modulate.a = 0.0
	if _bg_tween != null and _bg_tween.is_valid():
		_bg_tween.kill()
	_bg_tween = create_tween()
	_bg_tween.tween_property(_bg_b, "modulate:a", 1.0, BG_FADE_SEC)
	_bg_tween.tween_callback(func():
		_bg_a.texture = tex
		_bg_a.modulate.a = 1.0
		_bg_b.modulate.a = 0.0
	)


func _launch_minigame(id: String) -> void:
	var inst: Node = null
	match id:
		"budget_allocator":
			inst = BUDGET_ALLOCATOR.instantiate()
		"meeting_survival":
			inst = MEETING_SURVIVAL.instantiate()
		"emi_simulator":
			inst = EMI_SIMULATOR.instantiate()
		_:
			push_warning("GameScene: unknown minigame '%s'" % id)
			return
	_minigame_active = true
	add_child(inst)
	if inst.has_signal("completed"):
		inst.completed.connect(func(_score): _minigame_active = false)
	inst.tree_exited.connect(func(): _minigame_active = false)


func _launch_tactile(id: String) -> void:
	if not TACTILE_SCENES.has(id):
		push_warning("GameScene: unknown tactile '%s'" % id)
		return
	var path: String = TACTILE_SCENES[id]
	if not ResourceLoader.exists(path):
		push_warning("GameScene: tactile scene missing %s" % path)
		return
	var scene: PackedScene = load(path)
	var inst: Control = scene.instantiate()
	_minigame_active = true
	add_child(inst)
	if inst.has_signal("completed"):
		inst.completed.connect(func(_s):
			_minigame_active = false
			InkBridge.continue_story()
		)
	inst.tree_exited.connect(func(): _minigame_active = false)


func _on_stat_changed(id: String, old_v: float, new_v: float, source: String) -> void:
	if _transitioning:
		return
	if SILENT_DELTA_SOURCES.has(source):
		return
	var delta: float = new_v - old_v
	if abs(delta) < 0.5:
		return
	var toast: Control = STAT_DELTA_TOAST.instantiate()
	_delta_overlay.add_child(toast)
	if _delta_stack_reset_timer <= 0.0:
		_delta_stack_y = 0.0
	_delta_stack_reset_timer = DELTA_STACK_RESET_SEC
	toast.position = Vector2(0, _delta_stack_y)
	_delta_stack_y += DELTA_STACK_STEP
	toast.show_delta(id, delta, source)


func _offer_memory_echo(echo_id: String) -> void:
	print("[MemoryEcho] queued: ", echo_id)
