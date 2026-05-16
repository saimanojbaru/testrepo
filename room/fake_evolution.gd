extends Node3D
class_name FakeEvolution
## Fake-growth evolution. Scales the character root as a whole AND scales
## the head independently so babies have proportionally larger heads.
##
## Pre-flight fixes vs the supplied snippet:
##   - global_scale is read-only on Node3D in Godot 4 → use local `scale`.
##   - Vector3.slerp normalises non-unit vectors → use `lerp`.

@export_category("Body Component Links")
@export var head_mesh_path: NodePath
@export var torso_mesh_path: NodePath
@export var limbs_container_path: NodePath

var head_mesh: MeshInstance3D
var torso_mesh: MeshInstance3D
var limbs_container: Node3D

## Smooth interpolation rate (per second). 1.0 ≈ 1-second time constant.
@export var evolution_speed: float = 1.5

var target_overall_scale: Vector3 = Vector3(0.4, 0.4, 0.4)
var target_head_ratio:    Vector3 = Vector3(1.6, 1.6, 1.6)

var _current_age_months: int = 60  # default age 5


func _ready() -> void:
	if not head_mesh_path.is_empty():
		var h := get_node_or_null(head_mesh_path)
		if h is MeshInstance3D:
			head_mesh = h
	if not torso_mesh_path.is_empty():
		var t := get_node_or_null(torso_mesh_path)
		if t is MeshInstance3D:
			torso_mesh = t
	if not limbs_container_path.is_empty():
		var l := get_node_or_null(limbs_container_path)
		if l is Node3D:
			limbs_container = l
	scale = target_overall_scale
	if head_mesh:
		head_mesh.scale = target_head_ratio


func _process(delta: float) -> void:
	var t: float = clamp(evolution_speed * delta, 0.0, 1.0)
	# Local scale — global_scale is read-only in Godot 4.
	scale = scale.lerp(target_overall_scale, t)
	if head_mesh:
		head_mesh.scale = head_mesh.scale.lerp(target_head_ratio, t)


## Push age forward. Caller may be a UI button (Skip a year) or an auto-tick.
func process_growth_tick(months: int) -> void:
	_current_age_months = months
	var age_years: float = months / 12.0
	var progress: float = clamp(age_years / 20.0, 0.0, 1.0)

	var current_height: float = lerp(0.4, 1.0, progress)
	var current_width:  float = lerp(0.5, 1.0, progress)
	target_overall_scale = Vector3(current_width, current_height, current_width)

	# Infant heads are oversized; adult heads scale to parity.
	var head_factor: float = lerp(1.6, 1.0, progress)
	target_head_ratio = Vector3(head_factor, head_factor, head_factor)


func get_age_months() -> int:
	return _current_age_months
