extends Control
## Budget Allocator — MVP mini-game.
##
## Player allocates a monthly income across categories (Food, Family, Savings,
## Education, Fun). Random "surprise" expense may appear. Solution scoring
## drives Financial Awareness, Family Bond, Happiness.

signal completed(score: float)

const CATEGORIES := ["Food", "Family", "Savings", "Education", "Fun"]
const DEFAULT_INCOME := 30000.0

var _income: float = DEFAULT_INCOME
var _surprise: float = 0.0
var _surprise_label: String = ""
var _allocations: Dictionary = {}
var _sliders: Dictionary = {}
var _value_labels: Dictionary = {}


func _ready() -> void:
	_surprise = randf_range(0.0, 5000.0) if randf() < 0.6 else 0.0
	if _surprise > 0.0:
		var picks := ["medical bill", "festival expenses", "school fee top-up", "broken pressure cooker"]
		_surprise_label = picks[randi() % picks.size()]

	$Header.text = "Monthly income: ₹%d   Surprise: %s" % [
		int(_income),
		"none" if _surprise == 0.0 else "₹%d (%s)" % [int(_surprise), _surprise_label]
	]

	for c in CATEGORIES:
		_allocations[c] = _income / CATEGORIES.size()
		var row := _build_row(c, _allocations[c])
		$VBox.add_child(row)

	_refresh_remaining()
	$Submit.pressed.connect(_on_submit)
	$Cancel.pressed.connect(_on_cancel)
	ButtonFX.attach($Submit)
	ButtonFX.attach($Cancel)


func _build_row(label: String, initial: float) -> Node:
	var row := HBoxContainer.new()
	row.custom_minimum_size = Vector2(0, 80)
	var name_label := Label.new()
	name_label.text = label
	name_label.custom_minimum_size = Vector2(200, 0)
	name_label.add_theme_font_size_override("font_size", 24)
	var slider := HSlider.new()
	slider.min_value = 0
	slider.max_value = _income
	slider.step = 100
	slider.value = initial
	slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var value_label := Label.new()
	value_label.text = "₹%d" % int(initial)
	value_label.custom_minimum_size = Vector2(180, 0)
	value_label.add_theme_font_size_override("font_size", 24)
	slider.value_changed.connect(func(v):
		_allocations[label] = v
		value_label.text = "₹%d" % int(v)
		_refresh_remaining()
	)
	row.add_child(name_label)
	row.add_child(slider)
	row.add_child(value_label)
	_sliders[label] = slider
	_value_labels[label] = value_label
	return row


func _refresh_remaining() -> void:
	var total: float = 0.0
	for c in CATEGORIES:
		total += _allocations[c]
	var required: float = _income - _surprise
	var diff: float = required - total
	$Remaining.text = "Required to balance: ₹%d   Difference: ₹%d" % [int(required), int(diff)]
	$Submit.disabled = abs(diff) > 1.0


func _on_submit() -> void:
	var score := _score()
	StatEngine.modify("financial_awareness", score * 5.0, "budget_allocator")
	if _allocations["Family"] > _income * 0.15:
		StatEngine.modify("family_bond", 2.0, "budget_allocator")
	if _allocations["Fun"] > _income * 0.20:
		StatEngine.modify("happiness", 1.5, "budget_allocator")
	if _allocations["Savings"] > _income * 0.15:
		StatEngine.modify("savings", _allocations["Savings"], "budget_allocator")
	completed.emit(score)
	queue_free()


func _on_cancel() -> void:
	queue_free()


func _score() -> float:
	# Higher score when allocation has positive Savings AND covers family + food
	# adequately. Range roughly 0..1.
	var s: float = 0.0
	s += clamp(_allocations["Savings"] / (_income * 0.20), 0.0, 1.0) * 0.4
	s += clamp(_allocations["Family"]  / (_income * 0.15), 0.0, 1.0) * 0.2
	s += clamp(_allocations["Food"]    / (_income * 0.25), 0.0, 1.0) * 0.2
	s += clamp(_allocations["Education"] / (_income * 0.10), 0.0, 1.0) * 0.1
	s += clamp(_allocations["Fun"]     / (_income * 0.10), 0.0, 1.0) * 0.1
	return s
