extends TextureRect
## Drag source — the aloo paratha.

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_PASS


func _get_drag_data(_pos: Vector2) -> Variant:
	var preview := TextureRect.new()
	preview.texture = texture
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.custom_minimum_size = Vector2(160, 160)
	preview.size = preview.custom_minimum_size
	preview.modulate.a = 0.85
	var c := Control.new()
	c.add_child(preview)
	preview.position = -0.5 * preview.custom_minimum_size
	set_drag_preview(c)
	return {"item_type": "aloo_paratha", "source_node": self}
