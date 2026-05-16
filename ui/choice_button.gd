extends Button
## Choice button — wide, dark, readable, with subtle hover scale.

func _ready() -> void:
	autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	custom_minimum_size = Vector2(0, 140)
	clip_text = false
	mouse_entered.connect(func(): scale = Vector2(1.02, 1.02))
	mouse_exited.connect(func(): scale = Vector2(1.0, 1.0))


func set_choice_text(t: String) -> void:
	text = t
