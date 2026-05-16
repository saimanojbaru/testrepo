extends Camera3D
class_name RoomInteractor
## Isometric camera + click handler. Casts a ray on player taps, runs the
## InteractiveObject3D action, asks the scene for a pose update, spawns a
## floating Label3D reward.

@export_flags_3d_physics var interactive_layer_mask: int = 1
@export var character_path: NodePath = NodePath("../Character")

var character: FakeEvolution


func _ready() -> void:
	var c: Node = get_node_or_null(character_path)
	if c is FakeEvolution:
		character = c


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch and event.pressed:
		_perform_isometric_raycast(event.position)
		get_viewport().set_input_as_handled()
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_perform_isometric_raycast(event.position)
		get_viewport().set_input_as_handled()


func _perform_isometric_raycast(mouse_pos: Vector2) -> void:
	var ray_origin: Vector3 = project_ray_origin(mouse_pos)
	var ray_end: Vector3 = ray_origin + project_ray_normal(mouse_pos) * 100.0
	var space_state: PhysicsDirectSpaceState3D = get_world_3d().direct_space_state

	var query := PhysicsRayQueryParameters3D.create(ray_origin, ray_end)
	query.collision_mask = interactive_layer_mask

	var result: Dictionary = space_state.intersect_ray(query)
	if result.is_empty():
		return
	var collider: Object = result.get("collider")
	if collider is InteractiveObject3D:
		_execute_object_action(collider as InteractiveObject3D)


func _execute_object_action(object: InteractiveObject3D) -> void:
	object.apply_to_engine()
	# Ask the scene to refresh the character pose (hysteresis logic lives there).
	var scene_root: Node = get_tree().current_scene
	if scene_root and scene_root.has_method("request_pose_update"):
		scene_root.request_pose_update()
	_spawn_3d_reward_text(
		object.global_position + Vector3(0, 1.2, 0),
		object.reward_text,
		object.reward_color
	)


func _spawn_3d_reward_text(spawn_pos: Vector3, label_text: String, label_color: Color) -> void:
	var floating_lbl := Label3D.new()
	get_tree().current_scene.add_child(floating_lbl)
	floating_lbl.global_position = spawn_pos
	floating_lbl.text = label_text
	floating_lbl.modulate = label_color
	floating_lbl.font_size = 64
	floating_lbl.outline_size = 8
	floating_lbl.outline_modulate = Color(0, 0, 0, 0.8)
	floating_lbl.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	floating_lbl.no_depth_test = true

	var text_tween := create_tween()
	text_tween.parallel().tween_property(floating_lbl, "global_position:y",
		spawn_pos.y + 0.8, 1.4).set_trans(Tween.TRANS_SINE)
	text_tween.parallel().tween_property(floating_lbl, "modulate:a",
		0.0, 1.4).set_delay(0.4)
	text_tween.tween_callback(floating_lbl.queue_free)
