extends Node3D
## Childhood-bedroom scene controller. Owns the month ticker, drives the
## character evolution + vitals feedback into the AnimationTree, and hosts
## the "back to menu" + manual age-advance UI buttons.

@export var character_path: NodePath = NodePath("Character")
@export var anim_controller_path: NodePath = NodePath("Character/AnimController")

var character: CharacterEvolution
var anim_controller: CharacterAnimController
@export var months_per_real_second: float = 0.5

var _age_months: float = 60.0  # start at age 5
var _accum: float = 0.0


func _ready() -> void:
	var c: Node = get_node_or_null(character_path)
	if c is CharacterEvolution:
		character = c
	var a: Node = get_node_or_null(anim_controller_path)
	if a is CharacterAnimController:
		anim_controller = a
	if character:
		character.update_age_progression(int(_age_months))
	_push_vitals()
	$Hud/AgeButton.pressed.connect(_on_skip_year)
	$Hud/BackButton.pressed.connect(_on_back)
	ButtonFX.attach($Hud/AgeButton)
	ButtonFX.attach($Hud/BackButton)
	StatEngine.stat_changed.connect(_on_stat_changed)
	set_process(true)


func _process(delta: float) -> void:
	_accum += delta
	# Slow auto-tick of months — one month per ~2s by default.
	var months_step: float = months_per_real_second * delta
	if months_step >= 0.0001:
		_age_months += months_step
		if int(_age_months) != int(_age_months - months_step):
			character.update_age_progression(int(_age_months))
			$Hud/AgeLabel.text = "Age: %.1f years" % (_age_months / 12.0)


func _push_vitals() -> void:
	if anim_controller == null:
		return
	var stress: float = StatEngine.get_stat("stress")
	var energy: float = StatEngine.get_stat("energy")
	anim_controller.evaluate_character_vitals(stress, energy)


func _on_skip_year() -> void:
	_age_months = min(_age_months + 12.0, 22 * 12)
	character.update_age_progression(int(_age_months))
	$Hud/AgeLabel.text = "Age: %.1f years" % (_age_months / 12.0)


func _on_back() -> void:
	get_tree().change_scene_to_file("res://ui/main_menu.tscn")


func _on_stat_changed(_id: String, _old: float, _new: float, _source: String) -> void:
	_push_vitals()
