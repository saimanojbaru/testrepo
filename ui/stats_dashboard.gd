extends Control
## Stats dashboard — readable categorised view with pleasant typography.

const ROW_HEIGHT := 86
const CATEGORY_COLORS := {
	"core":          Color(0.96, 0.79, 0.55, 1),
	"career":        Color(0.85, 0.73, 0.95, 1),
	"financial":     Color(0.65, 0.95, 0.78, 1),
	"relationships": Color(0.95, 0.72, 0.78, 1),
	"wellbeing":     Color(0.78, 0.92, 0.95, 1),
	"meta":          Color(1.0, 0.95, 0.65, 1),
}


func _ready() -> void:
	$Back.pressed.connect(func(): get_tree().change_scene_to_file("res://ui/main_menu.tscn"))
	ButtonFX.attach($Back)
	_populate()
	StatEngine.stat_changed.connect(func(_a, _b, _c, _d): _populate())


func _populate() -> void:
	for c in $ScrollContainer/VBox.get_children():
		c.queue_free()
	var all := StatEngine.get_all()
	var keys: Array = all.keys()
	keys.sort()

	# Group by category for visual hierarchy.
	var by_cat: Dictionary = {}
	for id in keys:
		var def: Dictionary = StatEngine._definitions.get(id, {})
		var cat: String = String(def.get("category", "core"))
		if not by_cat.has(cat):
			by_cat[cat] = []
		by_cat[cat].append(id)

	var cat_order := ["core", "career", "financial", "wellbeing", "relationships", "meta"]
	for cat in cat_order:
		if not by_cat.has(cat):
			continue
		_add_category_header(cat)
		for id in by_cat[cat]:
			_add_stat_row(id, float(all[id]), cat)


func _add_category_header(cat: String) -> void:
	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(0, 12)
	$ScrollContainer/VBox.add_child(spacer)
	var label := Label.new()
	label.text = cat.capitalize()
	label.add_theme_font_size_override("font_size", 36)
	label.add_theme_color_override("font_color", CATEGORY_COLORS.get(cat, Color.WHITE))
	$ScrollContainer/VBox.add_child(label)


func _add_stat_row(id: String, value: float, cat: String) -> void:
	var row := HBoxContainer.new()
	row.custom_minimum_size = Vector2(0, ROW_HEIGHT)
	var name_label := Label.new()
	name_label.text = id.replace("_", " ").capitalize()
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_label.add_theme_font_size_override("font_size", 36)
	var def: Dictionary = StatEngine._definitions.get(id, {})
	var unit: String = String(def.get("unit", ""))
	var value_text: String
	if unit == "INR":
		value_text = "₹ %s" % _fmt_inr(value)
	else:
		value_text = "%.1f" % value
	var value_label := Label.new()
	value_label.text = value_text
	value_label.add_theme_font_size_override("font_size", 38)
	value_label.add_theme_color_override("font_color", CATEGORY_COLORS.get(cat, Color.WHITE))
	row.add_child(name_label)
	row.add_child(value_label)
	$ScrollContainer/VBox.add_child(row)


func _fmt_inr(amount: float) -> String:
	if amount >= 10000000:
		return "%.2f Cr" % (amount / 10000000.0)
	if amount >= 100000:
		return "%.2f L" % (amount / 100000.0)
	return "%d" % int(amount)
