extends Node3D
## Childhood-bedroom scene controller.
##
## Owns the month ticker, hands stat values to TweenAnimator (binary pose
## above/below stress threshold) and PaletteManager (skin tone shift, furniture
## aging). Listens to StatEngine signals from the 3D room AND the rest of the
## game so postural feedback is live.

@export var character_path: NodePath = NodePath("Character")
@export var tween_animator_path: NodePath = NodePath("Character/TweenAnimator")
@export var palette_manager_path: NodePath = NodePath("PaletteManager")
@export var months_per_real_second: float = 0.5

# Stress hysteresis — restart the breath loop on UPWARD crossing of HIGH
# and DOWNWARD crossing of LOW, to prevent flicker around the threshold.
const STRESS_HIGH := 50.0
const STRESS_LOW  := 35.0
# Skin tone uses a separate, higher hysteresis band.
const SKIN_STRESS_HIGH := 70.0
const SKIN_STRESS_LOW  := 50.0
# Furniture starts to look weathered after this many in-game months.
const FURNITURE_AGE_MONTHS := 180

var character: FakeEvolution
var tween_animator: TweenAnimator
var palette: PaletteManager

var _age_months: float = 60.0
var _last_stress_above_high: bool = false
var _skin_is_stressed: bool = false
var _furniture_aged_applied: bool = false


func _ready() -> void:
	var c: Node = get_node_or_null(character_path)
	if c is FakeEvolution:
		character = c
	var a: Node = get_node_or_null(tween_animator_path)
	if a is TweenAnimator:
		tween_animator = a
	var p: Node = get_node_or_null(palette_manager_path)
	if p is PaletteManager:
		palette = p

	if character:
		character.process_growth_tick(int(_age_months))
	_apply_character_palette()

	# Initial pose based on current stress.
	var stress: float = StatEngine.get_stat("stress")
	_last_stress_above_high = stress > STRESS_HIGH
	if tween_animator:
		tween_animator.start_procedural_idle_loop(stress)

	$Hud/AgeButton.pressed.connect(_on_skip_year)
	$Hud/BackButton.pressed.connect(_on_back)
	ButtonFX.attach($Hud/AgeButton)
	ButtonFX.attach($Hud/BackButton)
	StatEngine.stat_changed.connect(_on_stat_changed)
	set_process(true)


func _process(delta: float) -> void:
	var months_step: float = months_per_real_second * delta
	if months_step >= 0.0001:
		var prev_int: int = int(_age_months)
		_age_months += months_step
		if int(_age_months) != prev_int and character:
			character.process_growth_tick(int(_age_months))
			$Hud/AgeLabel.text = "Age: %.1f years" % (_age_months / 12.0)
			_maybe_age_furniture()


func _apply_character_palette() -> void:
	if palette == null:
		return
	# Skin (head + hair already share head_pivot — just colour the head mesh).
	var head: MeshInstance3D = get_node_or_null(
		"Character/CharacterRoot/HeadPivot/Head") as MeshInstance3D
	if head:
		palette.apply_flat_palette(head,
			palette.stressed_skin if _skin_is_stressed else palette.healthy_skin)
	# Body, limbs — unshaded with their existing albedos (shirt / pants).
	var body_paths := [
		"Character/CharacterRoot/TorsoPivot/Body",
		"Character/CharacterRoot/TorsoPivot/LeftArm",
		"Character/CharacterRoot/TorsoPivot/RightArm",
	]
	for path in body_paths:
		var m: MeshInstance3D = get_node_or_null(path) as MeshInstance3D
		if m and m.get_surface_override_material(0):
			var col: Color = (m.get_surface_override_material(0) as StandardMaterial3D).albedo_color
			palette.apply_flat_palette(m, col)
	var leg_paths := [
		"Character/CharacterRoot/TorsoPivot/LeftLeg",
		"Character/CharacterRoot/TorsoPivot/RightLeg",
	]
	for path in leg_paths:
		var m: MeshInstance3D = get_node_or_null(path) as MeshInstance3D
		if m and m.get_surface_override_material(0):
			var col: Color = (m.get_surface_override_material(0) as StandardMaterial3D).albedo_color
			palette.apply_flat_palette(m, col)


func _maybe_age_furniture() -> void:
	if _furniture_aged_applied or palette == null:
		return
	if int(_age_months) >= FURNITURE_AGE_MONTHS:
		var desk_mesh: Node = get_node_or_null("Furniture/Desk/Mesh")
		var books_mesh: Node = get_node_or_null("Furniture/Books/Mesh")
		# CSGBox3D meshes are NOT MeshInstance3D — palette doesn't touch them.
		# Set their material directly to the aged palette colour.
		if desk_mesh and desk_mesh is CSGShape3D:
			var aged_mat := StandardMaterial3D.new()
			aged_mat.albedo_color = palette.old_furniture
			aged_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
			desk_mesh.material = aged_mat
		if books_mesh and books_mesh is CSGShape3D:
			var aged_mat2 := StandardMaterial3D.new()
			aged_mat2.albedo_color = palette.old_furniture
			aged_mat2.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
			books_mesh.material = aged_mat2
		_furniture_aged_applied = true


func _on_skip_year() -> void:
	_age_months = min(_age_months + 12.0, 22 * 12)
	if character:
		character.process_growth_tick(int(_age_months))
	$Hud/AgeLabel.text = "Age: %.1f years" % (_age_months / 12.0)
	_maybe_age_furniture()


func _on_back() -> void:
	get_tree().change_scene_to_file("res://ui/main_menu.tscn")


func _on_stat_changed(id: String, _old: float, new_v: float, _source: String) -> void:
	if id != "stress":
		return
	# Hysteresis on the breath-loop threshold.
	if not _last_stress_above_high and new_v > STRESS_HIGH:
		_last_stress_above_high = true
		if tween_animator:
			tween_animator.start_procedural_idle_loop(new_v)
	elif _last_stress_above_high and new_v < STRESS_LOW:
		_last_stress_above_high = false
		if tween_animator:
			tween_animator.start_procedural_idle_loop(new_v)

	# Separate hysteresis band for skin tone.
	if not _skin_is_stressed and new_v > SKIN_STRESS_HIGH:
		_skin_is_stressed = true
		_apply_character_palette()
	elif _skin_is_stressed and new_v < SKIN_STRESS_LOW:
		_skin_is_stressed = false
		_apply_character_palette()


## Called by RoomInteractor when an object is tapped.
func request_pose_update() -> void:
	if tween_animator:
		tween_animator.start_procedural_idle_loop(StatEngine.get_stat("stress"))
