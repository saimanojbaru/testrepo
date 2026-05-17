class_name ButtonFX
extends Object
## Reusable Ghibli-soft hover + press tween. Idempotent — re-attaching is
## safe. Touch devices only fire button_down/button_up; desktop also fires
## mouse_entered/exited. Both are wired.

const HOVER_SCALE := Vector2(1.03, 1.03)
const PRESS_SCALE := Vector2(0.97, 0.97)
const HOVER_TINT  := Color(1.0, 1.0, 1.0, 1.0)
const HOVER_DUR   := 0.18
const PRESS_DUR   := 0.10


static func attach(btn: Button) -> void:
	if btn == null or btn.has_meta("button_fx_attached"):
		return
	btn.set_meta("button_fx_attached", true)
	btn.pivot_offset = btn.size / 2.0
	btn.resized.connect(func(): btn.pivot_offset = btn.size / 2.0)
	btn.mouse_entered.connect(func(): _tween_to(btn, HOVER_SCALE, HOVER_TINT, HOVER_DUR))
	btn.mouse_exited.connect(func():  _tween_to(btn, Vector2.ONE,  Color.WHITE, HOVER_DUR))
	btn.button_down.connect(func():   _tween_to(btn, PRESS_SCALE, HOVER_TINT, PRESS_DUR))
	btn.button_up.connect(func():     _tween_to(btn, Vector2.ONE,  Color.WHITE, HOVER_DUR))


static func attach_all(root: Node) -> void:
	for b in root.find_children("*", "Button", true, false):
		attach(b)


static func _tween_to(btn: Button, scale: Vector2, mod: Color, dur: float) -> void:
	var prev: Variant = btn.get_meta("button_fx_tween", null)
	if prev != null and prev is Tween and prev.is_valid():
		prev.kill()
	var t: Tween = btn.create_tween().set_parallel(true)
	t.tween_property(btn, "scale", scale, dur).set_trans(Tween.TRANS_SINE)
	t.tween_property(btn, "modulate", mod, dur)
	btn.set_meta("button_fx_tween", t)
