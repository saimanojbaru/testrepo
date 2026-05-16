extends Button
## Choice button — auto-sizes to its text. Hover/press feedback comes from
## ButtonFX (attached by parent scene).

func _ready() -> void:
	autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	clip_text = false
	custom_minimum_size = Vector2(0, 0)
	alignment = HORIZONTAL_ALIGNMENT_LEFT
	ButtonFX.attach(self)


func set_choice_text(t: String) -> void:
	text = "›  " + t
