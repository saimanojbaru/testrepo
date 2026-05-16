extends Control
## EMI Simulator — Chapter 3 / Chapter 4.
##
## Loan amount × interest × tenure → monthly EMI + total interest. Player
## tweaks sliders to find an EMI they can sustain. Stats: Financial Awareness
## up if EMI < 35% of income, Stress up otherwise.

signal completed(score: float)

@onready var _principal_slider: HSlider = $Principal/Slider
@onready var _rate_slider: HSlider     = $Rate/Slider
@onready var _tenure_slider: HSlider   = $Tenure/Slider
@onready var _output: Label            = $Output

var _income: float = 50000.0


func _ready() -> void:
	$Title.text = "EMI Simulator — find a sustainable loan"
	_principal_slider.value_changed.connect(func(_v): _recalc())
	_rate_slider.value_changed.connect(func(_v): _recalc())
	_tenure_slider.value_changed.connect(func(_v): _recalc())
	$Submit.pressed.connect(_on_submit)
	$Cancel.pressed.connect(_on_cancel)
	_recalc()


func _emi(p: float, r_yearly: float, years: float) -> float:
	if years <= 0.0:
		return p
	var n: float = years * 12.0
	var r: float = r_yearly / 12.0 / 100.0
	if r <= 0.000001:
		return p / n
	var factor: float = pow(1.0 + r, n)
	return p * r * factor / (factor - 1.0)


func _recalc() -> void:
	var p: float = _principal_slider.value
	var r: float = _rate_slider.value
	var t: float = _tenure_slider.value
	$Principal/Label.text = "Loan principal: ₹%d" % int(p)
	$Rate/Label.text = "Interest: %.1f%% p.a." % r
	$Tenure/Label.text = "Tenure: %d years" % int(t)
	var monthly: float = _emi(p, r, t)
	var total: float = monthly * t * 12.0
	var ratio: float = monthly / _income
	var sustainable := ratio < 0.35
	_output.text = "Monthly EMI: ₹%d  (%.0f%% of income)\nTotal interest: ₹%d\n%s" % [
		int(monthly), ratio * 100, int(total - p),
		"OK to take" if sustainable else "Too tight — try lower"
	]


func _on_submit() -> void:
	var p: float = _principal_slider.value
	var r: float = _rate_slider.value
	var t: float = _tenure_slider.value
	var monthly: float = _emi(p, r, t)
	var ratio: float = monthly / _income
	if ratio < 0.35:
		StatEngine.modify("financial_awareness", 6.0, "emi_simulator")
	else:
		StatEngine.modify("stress", 4.0, "emi_simulator")
	StatEngine.modify("debt", p, "emi_simulator")
	completed.emit(1.0 - ratio)
	queue_free()


func _on_cancel() -> void:
	queue_free()
