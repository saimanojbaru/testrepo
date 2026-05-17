extends Control
## Floating stat-delta toast — rises, fades, queue_frees.

const RISE_PX := 130.0
const FADE_IN := 0.18
const HOLD := 1.4
const FADE_OUT := 0.8


func show_delta(stat_id: String, delta: float, _source: String = "") -> void:
	var label: Label = $Label
	var positive: bool = delta > 0
	var col: Color = Color("7fd99a") if positive else Color("e8786e")
	var pretty: String = stat_id.replace("_", " ").capitalize()
	var amount_text: String
	if stat_id == "debt" or stat_id == "savings":
		amount_text = _format_inr(delta, positive)
	else:
		amount_text = "%s%d" % ["+" if positive else "-", int(abs(delta))]
	label.text = "%s  %s" % [pretty, amount_text]
	label.add_theme_color_override("font_color", col)
	label.modulate.a = 0.0
	var start_y: float = position.y

	var fade_in_tween := create_tween().set_parallel(true)
	fade_in_tween.tween_property(label, "modulate:a", 1.0, FADE_IN)
	fade_in_tween.tween_property(self, "position:y", start_y - RISE_PX, FADE_IN + HOLD + FADE_OUT) \
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)

	var fade_out_tween := create_tween()
	fade_out_tween.tween_interval(FADE_IN + HOLD)
	fade_out_tween.tween_property(label, "modulate:a", 0.0, FADE_OUT)
	fade_out_tween.tween_callback(queue_free)


func _format_inr(amount: float, positive: bool) -> String:
	var prefix: String = "+₹" if positive else "-₹"
	var a: float = abs(amount)
	if a >= 10000000:
		return "%s%.2f Cr" % [prefix, a / 10000000.0]
	if a >= 100000:
		return "%s%.2f L" % [prefix, a / 100000.0]
	return "%s%d" % [prefix, int(a)]
