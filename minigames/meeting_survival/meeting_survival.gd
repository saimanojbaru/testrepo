extends Control
## Meeting Survival — Chapter 3.
##
## Stay attentive across a 30s "boring meeting". Random prompts appear; tap
## the correct response quickly. Missing a prompt or wrong-tapping costs
## Reputation. Survival builds Soft Skills.

signal completed(score: float)

const DURATION := 18.0
const PROMPT_COUNT := 5
const PROMPTS := [
	{ "q": "Boss: 'What are your thoughts?'", "right": "Agree and propose a small refinement", "wrong": ["Stay silent", "I haven't been listening sorry"] },
	{ "q": "Boss: 'Can you own this action item?'", "right": "I can take it with one clarification", "wrong": ["Sure thing!", "Maybe someone else"] },
	{ "q": "Client: 'Will this go live Friday?'", "right": "Friday is tight; Monday is realistic", "wrong": ["Definitely Friday!", "Probably not"] },
	{ "q": "Peer presents your slide.", "right": "Acknowledge and add the data you collected", "wrong": ["Stay quiet", "Argue loudly"] },
	{ "q": "Boss: 'Quick status?'", "right": "Three bullets, no excuses", "wrong": ["Long story actually...", "All good"] }
]

var _score: int = 0
var _missed: int = 0
var _wrong: int = 0
var _prompt_index: int = 0
var _shuffled: Array = []
var _timer: float = DURATION
var _prompt_timer: float = 0.0
var _buttons: Array = []


func _ready() -> void:
	_shuffled = PROMPTS.duplicate()
	_shuffled.shuffle()
	$Title.text = "Meeting Survival — stay sharp"
	_next_prompt()
	set_process(true)


func _process(delta: float) -> void:
	_timer = max(0.0, _timer - delta)
	_prompt_timer = max(0.0, _prompt_timer - delta)
	$TimeLeft.text = "Time left: %.1fs   Score: %d" % [_timer, _score]
	if _prompt_timer <= 0.0 and _prompt_index < PROMPT_COUNT:
		_missed += 1
		_next_prompt()
	if _timer <= 0.0:
		_finish()


func _next_prompt() -> void:
	_clear_buttons()
	if _prompt_index >= PROMPT_COUNT:
		_finish()
		return
	var p: Dictionary = _shuffled[_prompt_index % _shuffled.size()]
	$Prompt.text = String(p["q"])
	_prompt_index += 1
	_prompt_timer = 3.5

	var options := [String(p["right"])]
	for w in p["wrong"]:
		options.append(String(w))
	options.shuffle()
	for opt in options:
		var b := Button.new()
		b.text = opt
		b.custom_minimum_size = Vector2(0, 72)
		b.add_theme_font_size_override("font_size", 22)
		b.pressed.connect(func(): _on_answer(opt == String(p["right"])))
		$Answers.add_child(b)
		_buttons.append(b)


func _on_answer(correct: bool) -> void:
	if correct:
		_score += 1
	else:
		_wrong += 1
	_next_prompt()


func _clear_buttons() -> void:
	for b in _buttons:
		b.queue_free()
	_buttons.clear()


func _finish() -> void:
	set_process(false)
	var ratio: float = float(_score) / float(PROMPT_COUNT)
	StatEngine.modify("soft_skills",   ratio * 8.0 - 2.0, "meeting_survival")
	StatEngine.modify("reputation",    ratio * 6.0 - _wrong * 2.0, "meeting_survival")
	StatEngine.modify("stress",        2.0 + _missed * 1.5, "meeting_survival")
	completed.emit(ratio)
	queue_free()
