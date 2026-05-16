// LUT-driven post-process shader.
//
// Apply as a CanvasItem material on a full-screen ColorRect over the game
// world (or attach as a SubViewport post-process). Samples TWO 256x16 LUT
// PNGs and cross-fades between them using global_lut_blend.
// Vibrancy and warmth then nudge HSV on top. Vignette darkens the edges.

shader_type canvas_item;

uniform sampler2D lut_from : repeat_disable, filter_linear;
uniform sampler2D lut_to   : repeat_disable, filter_linear;
uniform sampler2D SCREEN_TEXTURE : hint_screen_texture, filter_linear;

global uniform float global_mood_vibrancy = 1.0;
global uniform float global_mood_warmth   = 1.0;
global uniform float global_mood_density  = 0.3;
global uniform float global_lut_blend     = 0.0;
global uniform float global_bloom_intensity = 0.4;
global uniform float global_vignette      = 0.0;

vec3 apply_lut(sampler2D lut, vec3 color) {
	// 16-slice 256x16 LUT lookup.
	float blue_index = color.b * 15.0;
	float blue_lo = floor(blue_index);
	float blue_hi = min(blue_lo + 1.0, 15.0);
	float t = blue_index - blue_lo;

	vec2 uv_lo = vec2((blue_lo + color.r) / 16.0, color.g);
	vec2 uv_hi = vec2((blue_hi + color.r) / 16.0, color.g);

	vec3 c_lo = texture(lut, uv_lo).rgb;
	vec3 c_hi = texture(lut, uv_hi).rgb;
	return mix(c_lo, c_hi, t);
}

vec3 rgb_to_hsv(vec3 c) {
	float maxC = max(c.r, max(c.g, c.b));
	float minC = min(c.r, min(c.g, c.b));
	float delta = maxC - minC;
	float h = 0.0;
	if (delta > 0.0) {
		if (maxC == c.r) h = mod((c.g - c.b) / delta, 6.0);
		else if (maxC == c.g) h = (c.b - c.r) / delta + 2.0;
		else h = (c.r - c.g) / delta + 4.0;
		h /= 6.0;
	}
	float s = maxC > 0.0 ? delta / maxC : 0.0;
	return vec3(h, s, maxC);
}

vec3 hsv_to_rgb(vec3 c) {
	float h = c.x * 6.0;
	float s = c.y;
	float v = c.z;
	float x = (1.0 - abs(mod(h, 2.0) - 1.0)) * s * v;
	float m = v - s * v;
	vec3 rgb;
	if (h < 1.0) rgb = vec3(s*v, x, 0.0);
	else if (h < 2.0) rgb = vec3(x, s*v, 0.0);
	else if (h < 3.0) rgb = vec3(0.0, s*v, x);
	else if (h < 4.0) rgb = vec3(0.0, x, s*v);
	else if (h < 5.0) rgb = vec3(x, 0.0, s*v);
	else rgb = vec3(s*v, 0.0, x);
	return rgb + m;
}

void fragment() {
	vec3 src = texture(SCREEN_TEXTURE, SCREEN_UV).rgb;
	vec3 graded = mix(
		apply_lut(lut_from, src),
		apply_lut(lut_to, src),
		clamp(global_lut_blend, 0.0, 1.0)
	);

	// HSV nudges: vibrancy -> saturation; warmth -> hue shift toward orange.
	vec3 hsv = rgb_to_hsv(graded);
	hsv.y *= mix(0.4, 1.0, clamp(global_mood_vibrancy, 0.0, 1.0));
	hsv.x = mod(hsv.x + (global_mood_warmth - 1.0) * 0.02, 1.0);
	vec3 result = hsv_to_rgb(hsv);

	// Vignette.
	float dist = distance(SCREEN_UV, vec2(0.5));
	float vignette = smoothstep(0.4, 0.85, dist);
	result *= mix(1.0, 1.0 - vignette, clamp(global_vignette, 0.0, 1.0));

	COLOR = vec4(result, 1.0);
}
