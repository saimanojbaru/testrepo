extends Control
class_name TopMetricDashboard
## Persistent 3-icon HUD: Family / Career / Finance. Each child MetricStatusIcon
## subscribes to StatEngine directly. This script only handles dashboard-level
## concerns: fade in/out, critical-threshold warning toasts.

const TOAST_DURATION := 3.0
const FADE_DURATION := 0.4

var _fade_tween: Tween = null


func _ready() -> void:
	StatEngine.stat_changed.connect(_check_critical_threshold)


func set_hud_visible(visible_state: bool) -> void:
	if _fade_tween != null and _fade_tween.is_valid():
		_fade_tween.kill()
	_fade_tween = create_tween()
	_fade_tween.tween_property(self, "modulate:a", 1.0 if visible_state else 0.0, FADE_DURATION)


func _check_critical_threshold(id: String, old_v: float, new_v: float, _src: String) -> void:
	# Only fire on downward crossing of 20 to avoid spam.
	if new_v >= 20.0 or old_v < 20.0:
		return
	match id:
		"family_bond":
			_spawn_warning_toast("⚠  Family Crisis — Maa is losing hope.")
		"discipline":
			_spawn_warning_toast("⚠  Discipline Crash — you're slipping behind.")
		"financial_awareness":
			_spawn_warning_toast("⚠  Finance Risk — bills piling up.")


func _spawn_warning_toast(message: String) -> void:
	var lbl := Label.new()
	lbl.text = message
	lbl.modulate = Color(0.97, 0.45, 0.44, 1.0)
	lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lbl.add_theme_font_size_override("font_size", 28)
	lbl.anchor_right = 1.0
	add_child(lbl)
	lbl.position = Vector2(0, size.y + 6)
	lbl.modulate.a = 0.0
	var t := create_tween()
	t.tween_property(lbl, "modulate:a", 1.0, 0.25)
	t.tween_interval(TOAST_DURATION - 0.55)
	t.tween_property(lbl, "modulate:a", 0.0, 0.3)
	t.tween_callback(lbl.queue_free)
