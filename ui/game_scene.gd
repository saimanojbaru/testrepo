extends Control
## GameScene — primary playback scene. Wires InkBridge → DialogueBox/ChoicePanel,
## listens for mood requests, and routes mini-game tags to mini-game scenes.

@export var chapter_id: String = "chapter_01_innocent_spark"

@onready var _speaker: Label = $DialoguePanel/MarginContainer/VBoxContainer/Speaker
@onready var _text:    RichTextLabel = $DialoguePanel/MarginContainer/VBoxContainer/Body
@onready var _continue_hint: Label = $DialoguePanel/MarginContainer/VBoxContainer/ContinueHint
@onready var _choices_panel: VBoxContainer = $ChoicesPanel/MarginContainer/Choices
@onready var _choices_panel_root: PanelContainer = $ChoicesPanel

const CHOICE_BUTTON := preload("res://ui/choice_button.tscn")
const BUDGET_ALLOCATOR := preload("res://minigames/budget_allocator/budget_allocator.tscn")


func _ready() -> void:
	_choices_panel_root.visible = false
	InkBridge.dialogue_line.connect(_on_dialogue_line)
	InkBridge.choices_offered.connect(_on_choices_offered)
	InkBridge.mood_requested.connect(_on_mood_requested)
	InkBridge.knot_entered.connect(_on_knot_entered)
	InkBridge.story_finished.connect(_on_story_finished)
	InkBridge.load_story(chapter_id)


func _unhandled_input(event: InputEvent) -> void:
	if _choices_panel_root.visible:
		return
	if event is InputEventScreenTouch and event.pressed:
		InkBridge.continue_story()
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		InkBridge.continue_story()
	elif event.is_action_pressed("ui_accept"):
		InkBridge.continue_story()


func _on_dialogue_line(speaker: String, text: String, meta: Dictionary) -> void:
	_speaker.text = speaker
	_speaker.visible = not speaker.is_empty()
	_text.text = text
	_continue_hint.visible = true
	if meta.has("minigame"):
		_launch_minigame(String(meta["minigame"]))
	if meta.has("memory_echo"):
		_offer_memory_echo(String(meta["memory_echo"]))


func _on_choices_offered(choices: Array) -> void:
	for c in _choices_panel.get_children():
		c.queue_free()
	for i in choices.size():
		var btn := CHOICE_BUTTON.instantiate()
		btn.set_text(String(choices[i]))
		var idx := i
		btn.pressed.connect(func(): _on_choice_pressed(idx))
		_choices_panel.add_child(btn)
	_choices_panel_root.visible = true
	_continue_hint.visible = false


func _on_choice_pressed(index: int) -> void:
	_choices_panel_root.visible = false
	InkBridge.choose(index)


func _on_mood_requested(mood_id: String, duration: float) -> void:
	MoodController.transition_to(mood_id, duration)


func _on_knot_entered(knot: String) -> void:
	print("[Story] knot: ", knot)


func _on_story_finished() -> void:
	_speaker.visible = false
	_text.text = "[i]Chapter complete. Tap to return to main menu.[/i]"
	_choices_panel_root.visible = false


func _launch_minigame(id: String) -> void:
	var inst: Node = null
	match id:
		"budget_allocator":
			inst = BUDGET_ALLOCATOR.instantiate()
		_:
			push_warning("GameScene: unknown minigame '%s'" % id)
			return
	add_child(inst)


func _offer_memory_echo(echo_id: String) -> void:
	# Show a non-blocking journal prompt CTA. Implementation lives in journal.gd.
	print("[MemoryEcho] queued: ", echo_id)
