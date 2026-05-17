extends Control
## Pack Tiffin tactile scene — drag paratha onto the open tiffin.
## Emits `completed(score)` and queue_free()s after a short hold.

signal completed(score: float)

@onready var _tiffin: TextureRect = $Center/HBox/TiffinSlot/Tiffin
@onready var _hint: Label = $HintLabel


func _ready() -> void:
	_tiffin.tiffin_packed.connect(_on_packed)
	_hint.text = "Drag the paratha into the tiffin"


func _on_packed() -> void:
	_hint.text = "Tiffin packed. Have a good day."
	StatEngine.modify("family_bond", 3.0, "tactile_pack_tiffin")
	StatEngine.modify("happiness", 2.0, "tactile_pack_tiffin")
	await get_tree().create_timer(1.6).timeout
	completed.emit(1.0)
	queue_free()
