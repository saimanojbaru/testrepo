extends TextureRect
## Drop target — the open tiffin. On successful drop, swap to closed
## texture, bounce, emit tiffin_packed.

signal tiffin_packed

const CLOSED_TEX := preload("res://data/sprites/tiffin_closed.png")


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_PASS


func _can_drop_data(_pos: Vector2, data: Variant) -> bool:
	return typeof(data) == TYPE_DICTIONARY and data.get("item_type") == "aloo_paratha"


func _drop_data(_pos: Vector2, data: Variant) -> void:
	var src: Node = data.get("source_node")
	if src and is_instance_valid(src):
		src.hide()
	texture = CLOSED_TEX
	var t := create_tween()
	t.tween_property(self, "scale", Vector2(1.1, 1.1), 0.10).set_trans(Tween.TRANS_SINE)
	t.tween_property(self, "scale", Vector2(1.0, 1.0), 0.18).set_trans(Tween.TRANS_BOUNCE)
	t.tween_callback(func(): tiffin_packed.emit())
