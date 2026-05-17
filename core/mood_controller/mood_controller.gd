extends Node
## MoodController — drives the global audiovisual MoodVector.
##
## Reads chapter baseline from data/theming/moods.json. Cross-fades between
## moods over a configurable duration. Pushes uniforms into ShaderGlobals and
## target stem volumes into AudioDirector. Story scenes can request short-term
## mood overrides via push_override / pop_override.

signal mood_changed(mood_id: String)
signal mood_transition_finished()

const MOODS_FILE := "res://data/theming/moods.json"
const DEFAULT_TRANSITION_SEC := 4.0

var _moods: Dictionary = {}
var _current_mood: Dictionary = {}
var _target_mood: Dictionary = {}
var _transition_t: float = 1.0
var _transition_duration: float = DEFAULT_TRANSITION_SEC
var _override_stack: Array = []
var _current_mood_id: String = ""


func _ready() -> void:
	_load_moods()
	if _moods.has("ch1_innocent"):
		_apply_immediate(_moods["ch1_innocent"], "ch1_innocent")
	set_process(true)


func _load_moods() -> void:
	var resolved: String = ContentPath.resolve(MOODS_FILE)
	if not FileAccess.file_exists(resolved):
		push_error("MoodController: missing %s" % resolved)
		return
	var f := FileAccess.open(resolved, FileAccess.READ)
	var parsed: Variant = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) == TYPE_DICTIONARY:
		_moods = parsed


func transition_to(mood_id: String, duration: float = DEFAULT_TRANSITION_SEC) -> void:
	if not _moods.has(mood_id):
		push_warning("MoodController: unknown mood '%s'" % mood_id)
		return
	_target_mood = _moods[mood_id].duplicate(true)
	_transition_duration = max(0.01, duration)
	_transition_t = 0.0
	_current_mood_id = mood_id
	mood_changed.emit(mood_id)


func push_override(mood_id: String, duration: float = DEFAULT_TRANSITION_SEC) -> void:
	_override_stack.push_back(_current_mood_id)
	transition_to(mood_id, duration)


func pop_override(duration: float = DEFAULT_TRANSITION_SEC) -> void:
	if _override_stack.is_empty():
		return
	var prev: String = _override_stack.pop_back()
	transition_to(prev, duration)


func _process(delta: float) -> void:
	if _transition_t >= 1.0:
		return
	_transition_t = min(1.0, _transition_t + (delta / _transition_duration))
	_apply_blend(_current_mood, _target_mood, _transition_t)
	if _transition_t >= 1.0:
		_current_mood = _target_mood.duplicate(true)
		mood_transition_finished.emit()


func _apply_immediate(mood: Dictionary, mood_id: String) -> void:
	_current_mood = mood.duplicate(true)
	_target_mood = mood.duplicate(true)
	_transition_t = 1.0
	_current_mood_id = mood_id
	_apply_blend(mood, mood, 1.0)
	mood_changed.emit(mood_id)


func _apply_blend(from: Dictionary, to: Dictionary, t: float) -> void:
	var vibrancy: float = lerp(float(from.get("vibrancy", 1.0)), float(to.get("vibrancy", 1.0)), t)
	var warmth: float   = lerp(float(from.get("warmth", 1.0)),   float(to.get("warmth", 1.0)),   t)
	var density: float  = lerp(float(from.get("density", 0.5)),  float(to.get("density", 0.5)),  t)
	var tempo: float    = lerp(float(from.get("tempo", 0.6)),    float(to.get("tempo", 0.6)),    t)

	ShaderGlobals.set_uniform("global_mood_vibrancy", vibrancy)
	ShaderGlobals.set_uniform("global_mood_warmth", warmth)
	ShaderGlobals.set_uniform("global_mood_density", density)
	ShaderGlobals.set_uniform("global_lut_blend", t)
	ShaderGlobals.set_uniform("global_particle_density", lerp(0.5, 1.5, vibrancy))
	ShaderGlobals.set_uniform("global_bloom_intensity", lerp(0.2, 0.6, warmth))
	ShaderGlobals.set_uniform("global_vignette", lerp(0.0, 0.5, density))

	AudioDirector.set_stem_target("ambient",    lerp(0.6, 0.9, 1.0 - vibrancy))
	AudioDirector.set_stem_target("melody",     lerp(0.4, 1.0, vibrancy))
	AudioDirector.set_stem_target("percussion", lerp(0.0, 1.0, tempo))
	AudioDirector.set_stem_target("tension",    lerp(0.0, 1.0, density))


func current_mood_id() -> String:
	return _current_mood_id


func current_vector() -> Dictionary:
	return _current_mood.duplicate(true)
