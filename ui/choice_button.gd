extends Button
## Choice button — wraps Button with a small entry animation.

func set_text(t: String) -> void:
	text = t
	autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	custom_minimum_size = Vector2(0, 96)
