extends Node
class_name CharacterAnimController
## Reads Stress + Energy stats and drives an AnimationTree blend tree that
## interpolates between Idle, Slouch, and Heavy Exhaustion poses.
##
## The AnimationTree's BlendTree is built programmatically in _ready() so
## the .tscn stays simple — we don't have to hand-craft AnimationNodeBlendTree
## sub-resource syntax.

@export var anim_tree_path: NodePath = NodePath("../AnimationTree")
@export var animation_player_path: NodePath = NodePath("../AnimationPlayer")
@export var adjustment_rate: float = 1.5

var anim_tree: AnimationTree
var animation_player: AnimationPlayer
var target_stress_blend: float = 0.0
var target_exhaustion_blend: float = 0.0


func _ready() -> void:
	var t: Node = get_node_or_null(anim_tree_path)
	if t is AnimationTree:
		anim_tree = t
	var p: Node = get_node_or_null(animation_player_path)
	if p is AnimationPlayer:
		animation_player = p
	if anim_tree == null:
		push_warning("CharacterAnimController: anim_tree path '%s' not found" % anim_tree_path)
		return
	if animation_player == null:
		push_warning("CharacterAnimController: animation_player path '%s' not found" % animation_player_path)
		return
	_ensure_animations()
	_build_blend_tree()
	anim_tree.active = true


func _ensure_animations() -> void:
	if animation_player == null:
		push_warning("CharacterAnimController: no AnimationPlayer")
		return
	var lib_name: StringName = &""  # global library
	if not animation_player.has_animation_library(lib_name):
		animation_player.add_animation_library(lib_name, AnimationLibrary.new())
	var lib: AnimationLibrary = animation_player.get_animation_library(lib_name)
	if not lib.has_animation("idle"):
		lib.add_animation("idle", _make_idle_animation())
	if not lib.has_animation("stressed_slouch"):
		lib.add_animation("stressed_slouch", _make_slouch_animation())
	if not lib.has_animation("heavy_exhaustion"):
		lib.add_animation("heavy_exhaustion", _make_exhaustion_animation())


func _make_idle_animation() -> Animation:
	# Subtle breathing — vertical bob on the character root.
	var a := Animation.new()
	a.length = 3.2
	a.loop_mode = Animation.LOOP_LINEAR
	var t := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t, NodePath("CharacterRoot:position:y"))
	a.track_insert_key(t, 0.0, 0.0)
	a.track_insert_key(t, 1.6, 0.04)
	a.track_insert_key(t, 3.2, 0.0)
	return a


func _make_slouch_animation() -> Animation:
	# Forward tilt + head down: rotate Z slightly forward, lower head.
	var a := Animation.new()
	a.length = 2.4
	a.loop_mode = Animation.LOOP_LINEAR
	var t1 := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t1, NodePath("CharacterRoot:rotation:x"))
	a.track_insert_key(t1, 0.0, deg_to_rad(8))
	a.track_insert_key(t1, 1.2, deg_to_rad(12))
	a.track_insert_key(t1, 2.4, deg_to_rad(8))
	var t2 := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t2, NodePath("CharacterRoot/Head:position:y"))
	a.track_insert_key(t2, 0.0, -0.06)
	a.track_insert_key(t2, 1.2, -0.10)
	a.track_insert_key(t2, 2.4, -0.06)
	return a


func _make_exhaustion_animation() -> Animation:
	# Heavier slump, slower breathing.
	var a := Animation.new()
	a.length = 4.0
	a.loop_mode = Animation.LOOP_LINEAR
	var t1 := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t1, NodePath("CharacterRoot:rotation:x"))
	a.track_insert_key(t1, 0.0, deg_to_rad(18))
	a.track_insert_key(t1, 2.0, deg_to_rad(22))
	a.track_insert_key(t1, 4.0, deg_to_rad(18))
	var t2 := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t2, NodePath("CharacterRoot/Head:position:y"))
	a.track_insert_key(t2, 0.0, -0.14)
	a.track_insert_key(t2, 2.0, -0.18)
	a.track_insert_key(t2, 4.0, -0.14)
	var t3 := a.add_track(Animation.TYPE_VALUE)
	a.track_set_path(t3, NodePath("CharacterRoot:position:y"))
	a.track_insert_key(t3, 0.0, -0.05)
	a.track_insert_key(t3, 2.0, -0.08)
	a.track_insert_key(t3, 4.0, -0.05)
	return a


func _build_blend_tree() -> void:
	if anim_tree == null or animation_player == null:
		return
	anim_tree.anim_player = anim_tree.get_path_to(animation_player)
	var tree := AnimationNodeBlendTree.new()
	var idle := AnimationNodeAnimation.new()
	idle.animation = &"idle"
	var slouch := AnimationNodeAnimation.new()
	slouch.animation = &"stressed_slouch"
	var exhausted := AnimationNodeAnimation.new()
	exhausted.animation = &"heavy_exhaustion"
	var stress_blend := AnimationNodeBlend2.new()
	var exhaustion_blend := AnimationNodeBlend2.new()
	tree.add_node(&"idle", idle, Vector2(0, 0))
	tree.add_node(&"slouch", slouch, Vector2(0, 100))
	tree.add_node(&"exhausted", exhausted, Vector2(0, 200))
	tree.add_node(&"stress_blend", stress_blend, Vector2(300, 50))
	tree.add_node(&"exhaustion_blend", exhaustion_blend, Vector2(600, 100))
	tree.connect_node(&"stress_blend", 0, &"idle")
	tree.connect_node(&"stress_blend", 1, &"slouch")
	tree.connect_node(&"exhaustion_blend", 0, &"stress_blend")
	tree.connect_node(&"exhaustion_blend", 1, &"exhausted")
	tree.connect_node(&"output", 0, &"exhaustion_blend")
	anim_tree.tree_root = tree


func _process(delta: float) -> void:
	if anim_tree == null or anim_tree.tree_root == null:
		return
	var current_stress: float = anim_tree.get("parameters/stress_blend/blend_amount")
	var next_stress: float = move_toward(current_stress, target_stress_blend, adjustment_rate * delta)
	anim_tree.set("parameters/stress_blend/blend_amount", next_stress)

	var current_exhaustion: float = anim_tree.get("parameters/exhaustion_blend/blend_amount")
	var next_exhaustion: float = move_toward(current_exhaustion, target_exhaustion_blend, adjustment_rate * delta)
	anim_tree.set("parameters/exhaustion_blend/blend_amount", next_exhaustion)


## Stats 0..100 → blend weights 0..1.
func evaluate_character_vitals(stress: float, energy: float) -> void:
	target_stress_blend = clamp(stress / 100.0, 0.0, 1.0)
	target_exhaustion_blend = clamp((100.0 - energy) / 100.0, 0.0, 1.0)
