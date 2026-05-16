extends Control
## GameScene — visual-novel-style playback with auto-sizing dialogue,
## floating speaker nameplate, cross-faded scene backgrounds.

@export var chapter_id: String = "chapter_01_innocent_spark"

const TYPEWRITER_CHARS_PER_SEC := 48.0
const BG_FADE_SEC := 0.9
const CHOICE_BUTTON := preload("res://ui/choice_button.tscn")
const BUDGET_ALLOCATOR := preload("res://minigames/budget_allocator/budget_allocator.tscn")
const MEETING_SURVIVAL := preload("res://minigames/meeting_survival/meeting_survival.tscn")
const EMI_SIMULATOR := preload("res://minigames/emi_simulator/emi_simulator.tscn")

@onready var _bg_a: TextureRect           = $BackgroundA
@onready var _bg_b: TextureRect           = $BackgroundB
@onready var _fallback_bg: ColorRect      = $FallbackBackground
@onready var _dialogue: PanelContainer    = $DialoguePanel
@onready var _body: RichTextLabel         = $DialoguePanel/VBoxContainer/Body
@onready var _continue_hint: Label        = $DialoguePanel/VBoxContainer/ContinueHint
@onready var _speaker_label: Label        = $DialoguePanel/VBoxContainer/SpeakerLabel
@onready var _speaker_divider: HSeparator = $DialoguePanel/VBoxContainer/SpeakerDivider
@onready var _choices_root: VBoxContainer = $ChoicesContainer

var _full_text: String = ""
var _typewriter_t: float = 0.0
var _typewriter_done: bool = true
var _minigame_active: bool = false
var _current_bg_path: String = ""
var _bg_tween: Tween = null


func _ready() -> void:
	_choices_root.visible = false
	_continue_hint.visible = false
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_fallback_bg.visible = true   # until first background loads

	InkBridge.dialogue_line.connect(_on_dialogue_line)
	InkBridge.choices_offered.connect(_on_choices_offered)
	InkBridge.mood_requested.connect(_on_mood_requested)
	InkBridge.knot_entered.connect(_on_knot_entered)
	InkBridge.story_finished.connect(_on_story_finished)
	InkBridge.chapter_finished.connect(_on_chapter_finished)
	set_process(true)
	call_deferred("_start_story")


func _start_story() -> void:
	InkBridge.load_story(chapter_id)


func _input(event: InputEvent) -> void:
	if _choices_root.visible or _minigame_active:
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
		_body.visible_ratio = 1.0
		_continue_hint.visible = true
		return
	InkBridge.continue_story()


func _process(delta: float) -> void:
	if _typewriter_done:
		return
	_typewriter_t += delta * TYPEWRITER_CHARS_PER_SEC
	var total: int = _full_text.length()
	if total <= 0:
		_typewriter_done = true
		return
	_body.visible_ratio = clamp(_typewriter_t / float(total), 0.0, 1.0)
	if _body.visible_ratio >= 1.0:
		_typewriter_done = true
		_continue_hint.visible = true


func _on_dialogue_line(speaker: String, text: String, meta: Dictionary) -> void:
	# Background swap.
	if meta.has("background"):
		_set_background(String(meta["background"]))

	# Speaker name as the first row of the dialogue panel.
	if speaker.is_empty():
		_speaker_label.visible = false
		_speaker_divider.visible = false
	else:
		_speaker_label.text = speaker
		_speaker_label.visible = true
		_speaker_divider.visible = true

	_full_text = text
	_body.text = text
	_body.visible_ratio = 0.0
	_typewriter_t = 0.0
	_typewriter_done = false
	_continue_hint.visible = false

	if meta.has("minigame"):
		_launch_minigame(String(meta["minigame"]))
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
	# Re-anchor choices container so it grows upward to fit content.
	_choices_root.visible = true
	_continue_hint.visible = false
	# Hide dialogue while choosing.
	_dialogue.visible = false
	await get_tree().process_frame
	var needed: float = _choices_root.size.y + 40.0
	_choices_root.offset_top = -needed
	_choices_root.offset_bottom = -40.0


func _on_choice_pressed(index: int) -> void:
	_choices_root.visible = false
	_dialogue.visible = true
	InkBridge.choose(index)


func _on_mood_requested(mood_id: String, duration: float) -> void:
	MoodController.transition_to(mood_id, duration)


func _on_knot_entered(knot: String) -> void:
	print("[Story] knot: ", knot)


func _on_story_finished() -> void:
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_body.text = "[i]Your story is complete.\nTap to return to the main menu.[/i]"
	_full_text = _body.text
	_body.visible_ratio = 1.0
	_typewriter_done = true
	_continue_hint.visible = true
	_choices_root.visible = false
	await get_tree().create_timer(2.8).timeout
	get_tree().change_scene_to_file("res://ui/main_menu.tscn")


func _on_chapter_finished(_finished_id: String, next_chapter_id: String) -> void:
	chapter_id = next_chapter_id
	_speaker_label.visible = false
	_speaker_divider.visible = false
	_body.text = "[i]Chapter complete.\nLoading next chapter...[/i]"
	_full_text = _body.text
	_body.visible_ratio = 1.0
	_typewriter_done = true
	_continue_hint.visible = false
	await get_tree().create_timer(2.5).timeout
	InkBridge.load_story(next_chapter_id)
	SaveManager.save_now()


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
	# Cross-fade from A→B then swap.
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


func _offer_memory_echo(echo_id: String) -> void:
	print("[MemoryEcho] queued: ", echo_id)
