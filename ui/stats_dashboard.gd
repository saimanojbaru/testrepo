extends Control
## Stats dashboard — read-only view of every tracked stat, grouped by category.

const ROW_HEIGHT := 64


func _ready() -> void:
	$Back.pressed.connect(func(): get_tree().change_scene_to_file("res://ui/main_menu.tscn"))
	_populate()
	StatEngine.stat_changed.connect(func(_a, _b, _c, _d): _populate())


func _populate() -> void:
	for c in $ScrollContainer/VBox.get_children():
		c.queue_free()
	var all := StatEngine.get_all()
	var keys := all.keys()
	keys.sort()
	for id in keys:
		var row := HBoxContainer.new()
		row.custom_minimum_size = Vector2(0, ROW_HEIGHT)
		var name_label := Label.new()
		name_label.text = id
		name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		name_label.add_theme_font_size_override("font_size", 24)
		var value_label := Label.new()
		value_label.text = "%.1f" % float(all[id])
		value_label.add_theme_font_size_override("font_size", 24)
		row.add_child(name_label)
		row.add_child(value_label)
		$ScrollContainer/VBox.add_child(row)
