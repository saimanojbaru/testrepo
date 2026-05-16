extends StaticBody3D
class_name InteractiveObject3D
## A clickable object in the isometric room. Holds the stat deltas and the
## floating-reward text that should appear when the player taps it.

@export var object_name: String = "Object"

## Free-text reward shown over the object on tap. Newlines OK.
@export_multiline var reward_text: String = "+5 Curiosity"

## Color the floating Label3D should adopt (warm Ghibli palette).
@export var reward_color: Color = Color(0.96, 0.79, 0.55)

@export_group("Stat Effects (applied via StatEngine on tap)")
## Each entry is a Dictionary: { "stat": "discipline", "delta": 5 }.
## We use the typed-array escape hatch (Array) since GDScript can't directly
## declare Array[Dictionary] for export with structured fields in 4.3.
@export var stat_effects: Array = [
	{"stat": "curiosity", "delta": 5},
]

@export_group("Simulated Vitals")
@export var gives_stress: int = 0
@export var drains_energy: int = 0


func apply_to_engine() -> void:
	for effect in stat_effects:
		if typeof(effect) != TYPE_DICTIONARY:
			continue
		var stat: String = String(effect.get("stat", ""))
		var delta: float = float(effect.get("delta", 0.0))
		if stat.is_empty():
			continue
		StatEngine.modify(stat, delta, "room_interaction:" + object_name.to_lower())
	# Apply secondary vitals if defined.
	if gives_stress != 0:
		StatEngine.modify("stress", float(gives_stress), "room_interaction:" + object_name.to_lower())
	if drains_energy != 0:
		StatEngine.modify("energy", -float(drains_energy), "room_interaction:" + object_name.to_lower())
