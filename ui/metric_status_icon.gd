extends Control
class_name MetricStatusIcon
## Persistent top-of-screen metric icon. Subscribes directly to StatEngine.
## Healthy (>60) glows green, warning (30-60) gold, danger (<30) crimson +
## panic-shake for ~1.4 s. Scale tracks ratio so the icon literally grows
## as the metric improves.

@export var stat_id: String = "family_bond"
@export var icon_texture: Texture2D
@export var show_value: bool = true

const HEALTHY := Color(0.29, 0.87, 0.50, 1.0)  # 4ade80
const WARNING := Color(0.98, 0.80, 0.13, 1.0)  # facc15
const DANGER  := Color(0.97, 0.45, 0.44, 1.0)  # f87171

const SHAKE_AMP_PX := 3.0
const PANIC_DURATION := 1.4

@onready var _icon: TextureRect = $Icon
@onready var _label: Label = $ValueLabel

var _target_scale: Vector2 = Vector2.ONE
var _is_panicking: bool = false
var _panic_t: float = 0.0
var _origin: Vector2


func _ready() -> void:
	pivot_offset = size / 2.0
	resized.connect(func(): pivot_offset = size / 2.0)
	_origin = position
	if _icon and icon_texture:
		_icon.texture = icon_texture
	if _label:
		_label.visible = show_value
	StatEngine.stat_changed.connect(_on_stat_changed)
	update_visual(StatEngine.get_stat(stat_id))
	_start_ambient_breath()


func _on_stat_changed(id: String, _old: float, new_v: float, _source: String) -> void:
	if id != stat_id:
		return
	update_visual(new_v)


func update_visual(value: float) -> void:
	var ratio: float = clamp(value / 100.0, 0.0, 1.0)
	var tint_tween := create_tween()
	if value > 60.0:
		tint_tween.tween_property(self, "modulate", HEALTHY, 0.4)
		_is_panicking = false
	elif value > 30.0:
		tint_tween.tween_property(self, "modulate", WARNING, 0.4)
		_is_panicking = false
	else:
		tint_tween.tween_property(self, "modulate", DANGER, 0.2)
		_trigger_panic()

	_target_scale = Vector2.ONE * lerp(0.85, 1.25, ratio)
	var surge := create_tween()
	surge.tween_property(self, "scale", _target_scale * 1.18, 0.10).set_trans(Tween.TRANS_SINE)
	surge.tween_property(self, "scale", _target_scale, 0.15).set_trans(Tween.TRANS_BOUNCE)

	if _label and show_value:
		_label.text = "%d" % int(round(value))


func _start_ambient_breath() -> void:
	var loop := create_tween().set_loops()
	loop.tween_property(self, "scale", _target_scale * 1.04, 1.4).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	loop.tween_property(self, "scale", _target_scale, 1.6).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


func _trigger_panic() -> void:
	if _is_panicking:
		return
	_is_panicking = true
	_panic_t = PANIC_DURATION


func _process(delta: float) -> void:
	if not _is_panicking:
		return
	_panic_t -= delta
	if _panic_t <= 0.0:
		position = _origin
		_is_panicking = false
		return
	position = _origin + Vector2(
		randf_range(-SHAKE_AMP_PX, SHAKE_AMP_PX),
		randf_range(-SHAKE_AMP_PX, SHAKE_AMP_PX)
	)
