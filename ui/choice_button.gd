extends Button
## Choice button — auto-sizes to its text, wraps to next line if needed.
## Uses BBCode-style "›" prefix to feel less like a generic form button.

func _ready() -> void:
	autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	clip_text = false
	custom_minimum_size = Vector2(0, 0)
	alignment = HORIZONTAL_ALIGNMENT_LEFT
	mouse_entered.connect(func():
		var t := create_tween()
		t.tween_property(self, "scale", Vector2(1.02, 1.02), 0.12)
	)
	mouse_exited.connect(func():
		var t := create_tween()
		t.tween_property(self, "scale", Vector2(1.0, 1.0), 0.12)
	)


func set_choice_text(t: String) -> void:
	text = "›  " + t
