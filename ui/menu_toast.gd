extends Control
class_name MenuToast
## Bottom-of-screen status message used by Main Menu actions (e.g. update check).
## Fades in, holds, fades out, frees itself.

const FADE_IN := 0.22
const FADE_OUT := 0.4


func show_message(text: String, hold_seconds: float = 3.6, color: Color = Color(0.98, 0.94, 0.86, 1.0)) -> void:
	var lbl: Label = $Label
	lbl.text = text
	lbl.modulate = color
	modulate.a = 0.0
	var t := create_tween()
	t.tween_property(self, "modulate:a", 1.0, FADE_IN)
	t.tween_interval(hold_seconds)
	t.tween_property(self, "modulate:a", 0.0, FADE_OUT)
	t.tween_callback(queue_free)
