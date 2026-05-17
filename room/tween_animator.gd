extends Node
class_name TweenAnimator
## Procedural breath loop — rotates torso + head pivots in a continuous
## ease-in-out cycle. Above the stress threshold the loop is rebuilt with
## a permanent forward hunch and a faster cadence.

@export var torso_pivot_path: NodePath
@export var head_pivot_path: NodePath
@export var stress_threshold: float = 50.0

var torso_pivot: Node3D
var head_pivot: Node3D
var _loop_tween: Tween
var _is_stressed_pose: bool = false


func _ready() -> void:
	if not torso_pivot_path.is_empty():
		var t := get_node_or_null(torso_pivot_path)
		if t is Node3D:
			torso_pivot = t
	if not head_pivot_path.is_empty():
		var h := get_node_or_null(head_pivot_path)
		if h is Node3D:
			head_pivot = h
	start_procedural_idle_loop(0.0)


## Restart the breath loop. Hysteresis lives in the caller — this method
## just rebuilds whatever state it's told.
func start_procedural_idle_loop(stress_level: float) -> void:
	if torso_pivot == null or head_pivot == null:
		return
	if _loop_tween != null and _loop_tween.is_valid():
		_loop_tween.kill()

	_is_stressed_pose = stress_level > stress_threshold

	var base_torso_rot: float = 0.0
	var base_head_rot:  float = 0.0
	var speed_modifier: float = 1.0
	if _is_stressed_pose:
		base_torso_rot = 0.25
		base_head_rot = -0.15
		speed_modifier = 1.6

	_loop_tween = create_tween().set_loops()

	# Breathe in.
	_loop_tween.parallel().tween_property(torso_pivot, "rotation:x",
		base_torso_rot + 0.04, 1.2 / speed_modifier) \
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	_loop_tween.parallel().tween_property(head_pivot, "rotation:x",
		base_head_rot - 0.02, 1.2 / speed_modifier) \
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)

	# Hold one frame, then breathe out.
	_loop_tween.chain()
	_loop_tween.parallel().tween_property(torso_pivot, "rotation:x",
		base_torso_rot, 1.5 / speed_modifier) \
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	_loop_tween.parallel().tween_property(head_pivot, "rotation:x",
		base_head_rot, 1.5 / speed_modifier) \
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


func is_stressed_pose() -> bool:
	return _is_stressed_pose
