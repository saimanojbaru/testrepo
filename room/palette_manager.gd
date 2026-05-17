extends Node
class_name PaletteManager
## Flat-shaded material factory. Generates StandardMaterial3D with
## SHADING_MODE_UNSHADED so meshes read as cel-shaded silhouettes
## regardless of lighting. Used for character + furniture aging,
## stress-driven skin tone shift.

@export var healthy_skin: Color = Color("ffe5d9")
@export var stressed_skin: Color = Color("d8cfca")
@export var clean_furniture: Color = Color("b5835a")
@export var old_furniture: Color = Color("6d594f")


func apply_flat_palette(mesh_instance: MeshInstance3D, target_color: Color) -> void:
	if mesh_instance == null:
		return
	var mat := StandardMaterial3D.new()
	mat.albedo_color = target_color
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.roughness = 1.0
	mat.metallic = 0.0
	mesh_instance.set_surface_override_material(0, mat)


## Convenience: apply flat palette to every direct MeshInstance3D under a
## node tree (e.g. an entire piece of furniture).
func apply_flat_palette_recursive(root: Node, target_color: Color) -> void:
	if root == null:
		return
	for child in root.find_children("*", "MeshInstance3D", true, false):
		apply_flat_palette(child, target_color)
