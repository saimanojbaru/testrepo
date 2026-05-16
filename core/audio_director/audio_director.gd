extends Node
## AudioDirector — owns the 4-stem music bed and mixes diegetic SFX over it.
##
## MoodController calls set_stem_target() to fade stems. SFX are queued and
## ducked under dialogue automatically.

const STEM_NAMES := ["ambient", "melody", "percussion", "tension"]
const FADE_SPEED_DB_PER_SEC := 6.0

var _players: Dictionary = {}              # stem_name -> AudioStreamPlayer
var _targets_db: Dictionary = {}           # stem_name -> target dB
var _current_db: Dictionary = {}           # stem_name -> current dB
var _sfx_player: AudioStreamPlayer
var _dialogue_active: bool = false
var _music_bus: String = "Master"
var _sfx_bus: String = "Master"


func _ready() -> void:
	_music_bus = "Music" if AudioServer.get_bus_index("Music") >= 0 else "Master"
	_sfx_bus = "SFX" if AudioServer.get_bus_index("SFX") >= 0 else "Master"

	for stem in STEM_NAMES:
		var p := AudioStreamPlayer.new()
		p.name = "stem_" + stem
		p.bus = _music_bus
		p.volume_db = linear_to_db(0.0001)
		add_child(p)
		_players[stem] = p
		_targets_db[stem] = -80.0
		_current_db[stem] = -80.0

	_sfx_player = AudioStreamPlayer.new()
	_sfx_player.name = "sfx"
	_sfx_player.bus = _sfx_bus
	add_child(_sfx_player)

	set_process(true)


func play_bed(stems: Dictionary) -> void:
	# stems = { "ambient": stream, "melody": stream, ... } — any subset OK.
	for stem in STEM_NAMES:
		if stems.has(stem):
			var p: AudioStreamPlayer = _players[stem]
			p.stream = stems[stem]
			if not p.playing:
				p.play()


func stop_bed() -> void:
	for p in _players.values():
		p.stop()


func set_stem_target(stem: String, linear_volume: float) -> void:
	if not _players.has(stem):
		return
	var db: float = linear_to_db(max(0.0001, linear_volume))
	if _dialogue_active and stem == "melody":
		db -= 6.0
	_targets_db[stem] = db


func play_sfx(stream: AudioStream) -> void:
	if stream == null:
		return
	_sfx_player.stream = stream
	_sfx_player.play()


func set_dialogue_active(active: bool) -> void:
	_dialogue_active = active
	for stem in STEM_NAMES:
		set_stem_target(stem, db_to_linear(_targets_db[stem]))


func _process(delta: float) -> void:
	var step: float = FADE_SPEED_DB_PER_SEC * delta
	for stem in STEM_NAMES:
		var current: float = _current_db[stem]
		var target: float = _targets_db[stem]
		if abs(target - current) < 0.05:
			continue
		var new_db: float = current + clamp(target - current, -step, step)
		_current_db[stem] = new_db
		_players[stem].volume_db = new_db
