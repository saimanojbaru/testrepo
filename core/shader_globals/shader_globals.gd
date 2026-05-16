extends Node
## ShaderGlobals — single source of truth for visual-mood shader uniforms.
##
## Every background, character, and post-process shader samples these globals.
## MoodController writes here; nothing else should.

const UNIFORMS := {
	"global_mood_vibrancy":     1.0,
	"global_mood_warmth":       1.0,
	"global_mood_density":      0.3,
	"global_lut_blend":         0.0,
	"global_particle_density":  1.0,
	"global_bloom_intensity":   0.4,
	"global_vignette":          0.0,
}


func _ready() -> void:
	for key in UNIFORMS.keys():
		_ensure_global(key, UNIFORMS[key])


func _ensure_global(name: String, default_value: Variant) -> void:
	if RenderingServer.global_shader_parameter_get(name) == null:
		RenderingServer.global_shader_parameter_add(
			name,
			RenderingServer.GLOBAL_VAR_TYPE_FLOAT,
			default_value
		)
	else:
		RenderingServer.global_shader_parameter_set(name, default_value)


func set_uniform(name: String, value: float) -> void:
	RenderingServer.global_shader_parameter_set(name, value)


func get_uniform(name: String) -> float:
	var v = RenderingServer.global_shader_parameter_get(name)
	return float(v) if v != null else 0.0
