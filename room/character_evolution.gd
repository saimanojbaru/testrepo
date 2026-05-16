extends Node3D
class_name CharacterEvolution
## 3D Evolution Engine — scales the character mesh smoothly as months pass,
## and morphs blend shapes (when available on the mesh).

@export_category("Growth Configuration")
@export var character_mesh: MeshInstance3D
@export var base_growth_curve: Curve

## Smooth interpolation speed for physical transformations.
@export var growth_speed: float = 2.0

## Body parts that scale independently to fake limb-proportion shifts when
## the mesh has no blend shapes. Each entry: { node: Node3D, base_y: float,
## base_pos: Vector3 }. Populated by sub-classes / the bedroom scene.
@export var limbs: Array[Node3D] = []

var target_scale: Vector2 = Vector2(1.0, 1.0)  # X/Z (thickness), Y (height)
var _current_scale_3d: Vector3 = Vector3(0.5, 0.4, 0.5)  # actual interpolated
var target_blend_shape: float = 0.0
var _current_age_months: int = 0


func _ready() -> void:
	if base_growth_curve == null:
		base_growth_curve = _build_default_curve()
	target_scale = Vector2(0.5, 0.4)
	target_blend_shape = 0.0
	_current_scale_3d = Vector3(0.5, 0.4, 0.5)
	scale = _current_scale_3d


func _build_default_curve() -> Curve:
	var c := Curve.new()
	c.add_point(Vector2(0.0, 0.0))
	c.add_point(Vector2(0.45, 0.55))
	c.add_point(Vector2(0.75, 0.88))
	c.add_point(Vector2(1.0, 1.0))
	return c


func _process(delta: float) -> void:
	# 1. Smoothly interpolate height and thickness — lerp on Vector3 directly
	#    (Basis.slerp does not support non-uniform scale).
	var target_3d := Vector3(target_scale.x, target_scale.y, target_scale.x)
	_current_scale_3d = _current_scale_3d.lerp(target_3d, clamp(growth_speed * delta, 0.0, 1.0))
	scale = _current_scale_3d

	# 2. Optional: morph blend shapes if the mesh has any.
	if character_mesh and character_mesh.mesh != null:
		var count: int = 0
		if character_mesh.has_method("get_blend_shape_count"):
			count = character_mesh.get_blend_shape_count()
		if count > 0:
			var current_blend: float = character_mesh.get_blend_shape_value(0)
			var new_blend: float = move_toward(current_blend, target_blend_shape, growth_speed * delta)
			character_mesh.set_blend_shape_value(0, new_blend)


## Called by the global game engine whenever time steps forward.
func update_age_progression(months: int) -> void:
	_current_age_months = months
	var age_in_years: float = months / 12.0
	var progression_ratio: float = clamp(age_in_years / 22.0, 0.0, 1.0)
	var curve_modifier: float = base_growth_curve.sample(progression_ratio)
	var height_scale: float = lerp(0.4, 1.0, curve_modifier)
	var width_scale: float = lerp(0.5, 1.0, curve_modifier)
	target_scale = Vector2(width_scale, height_scale)
	target_blend_shape = progression_ratio


func get_age_months() -> int:
	return _current_age_months
