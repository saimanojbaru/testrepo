extends Button
## Choice button — auto-sizes to its text. Hover/press feedback comes from
## ButtonFX (attached by parent scene). All five font_color states are
## overridden explicitly so theme cascade can never make text disappear.

const CREAM := Color(0.98, 0.94, 0.86, 1.0)
const GOLD  := Color(1.00, 0.82, 0.40, 1.0)


func _ready() -> void:
	autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	clip_text = false
	custom_minimum_size = Vector2(0, 72)
	alignment = HORIZONTAL_ALIGNMENT_LEFT
	size_flags_horizontal = Control.SIZE_FILL
	_force_font_colors()
	ButtonFX.attach(self)


func _force_font_colors() -> void:
	add_theme_color_override("font_color",          CREAM)
	add_theme_color_override("font_hover_color",    GOLD)
	add_theme_color_override("font_pressed_color",  GOLD)
	add_theme_color_override("font_focus_color",    CREAM)
	add_theme_color_override("font_disabled_color", CREAM * 0.55)


func set_choice_text(t: String) -> void:
	text = "›  " + t
	_force_font_colors()
