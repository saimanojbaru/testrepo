extends Control
## GameScene — primary playback. Listens to InkBridge, presents dialogue
## with a typewriter effect, surfaces choices, routes mini-game tags.

@export var chapter_id: String = "chapter_01_innocent_spark"

const TYPEWRITER_CHARS_PER_SEC := 55.0
const CHOICE_BUTTON := preload("res://ui/choice_button.tscn")
const BUDGET_ALLOCATOR := preload("res://minigames/budget_allocator/budget_allocator.tscn")
const MEETING_SURVIVAL := preload("res://minigames/meeting_survival/meeting_survival.tscn")
const EMI_SIMULATOR := preload("res://minigames/emi_simulator/emi_simulator.tscn")

@onready var _speaker: Label             = $DialoguePanel/MarginContainer/VBoxContainer/Speaker
@onready var _text:    RichTextLabel     = $DialoguePanel/MarginContainer/VBoxContainer/Body
@onready var _continue_hint: Label       = $DialoguePanel/MarginContainer/VBoxContainer/ContinueHint
@onready var _choices_box: VBoxContainer = $ChoicesPanel/MarginContainer/VBoxContainer/Choices
@onready var _choices_root: PanelContainer = $ChoicesPanel

var _full_text: String = ""
var _typewriter_t: float = 0.0
var _typewriter_done: bool = true
var _minigame_active: bool = false


func _ready() -> void:
	_choices_root.visible = false
	_continue_hint.visible = false
	InkBridge.dialogue_line.connect(_on_dialogue_line)
	InkBridge.choices_offered.connect(_on_choices_offered)
	InkBridge.mood_requested.connect(_on_mood_requested)
	InkBridge.knot_entered.connect(_on_knot_entered)
	InkBridge.story_finished.connect(_on_story_finished)
	InkBridge.chapter_finished.connect(_on_chapter_finished)
	set_process(true)
	# Start the story on the next frame so signal connections stabilise.
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
		# Skip typewriter on first tap.
		_typewriter_done = true
		_text.visible_ratio = 1.0
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
	_text.visible_ratio = clamp(_typewriter_t / float(total), 0.0, 1.0)
	if _text.visible_ratio >= 1.0:
		_typewriter_done = true
		_continue_hint.visible = true


func _on_dialogue_line(speaker: String, text: String, meta: Dictionary) -> void:
	_speaker.text = speaker
	_speaker.visible = not speaker.is_empty()
	_full_text = text
	_text.text = text
	_text.visible_ratio = 0.0
	_typewriter_t = 0.0
	_typewriter_done = false
	_continue_hint.visible = false
	if meta.has("minigame"):
		_launch_minigame(String(meta["minigame"]))
	if meta.has("memory_echo"):
		_offer_memory_echo(String(meta["memory_echo"]))


func _on_choices_offered(choices: Array) -> void:
	for c in _choices_box.get_children():
		c.queue_free()
	for i in choices.size():
		var btn := CHOICE_BUTTON.instantiate()
		btn.set_choice_text(String(choices[i]))
		var idx: int = i
		btn.pressed.connect(func(): _on_choice_pressed(idx))
		_choices_box.add_child(btn)
	_choices_root.visible = true
	_continue_hint.visible = false


func _on_choice_pressed(index: int) -> void:
	_choices_root.visible = false
	InkBridge.choose(index)


func _on_mood_requested(mood_id: String, duration: float) -> void:
	MoodController.transition_to(mood_id, duration)


func _on_knot_entered(knot: String) -> void:
	print("[Story] knot: ", knot)


func _on_story_finished() -> void:
	_speaker.visible = false
	_text.text = "[i]Your story is complete.\nTap to return to the main menu.[/i]"
	_full_text = _text.text
	_text.visible_ratio = 1.0
	_typewriter_done = true
	_continue_hint.visible = true
	_choices_root.visible = false
	await get_tree().create_timer(2.5).timeout
	get_tree().change_scene_to_file("res://ui/main_menu.tscn")


func _on_chapter_finished(_finished_id: String, next_chapter_id: String) -> void:
	chapter_id = next_chapter_id
	_speaker.visible = false
	_text.text = "[i]Chapter complete.\nLoading next chapter...[/i]"
	_full_text = _text.text
	_text.visible_ratio = 1.0
	_typewriter_done = true
	_continue_hint.visible = false
	await get_tree().create_timer(2.5).timeout
	InkBridge.load_story(next_chapter_id)
	SaveManager.save_now()


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
