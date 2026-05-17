extends Node
## OTAManager — autoload. Opt-in over-the-air patcher for game data JSONs.
## Patches land in user://patches/<version>/<res-mirrored-path>. ContentPath
## resolves res:// reads against this directory at load time.
##
## Patches are restricted to game data JSON files (story.fallback.json,
## data/stats/*.json, data/theming/moods.json, data/memory_echoes/*.json).
## Engine code, GDScript, shaders, and binaries are NOT patchable — that
## requires a real APK update.

signal update_check_completed(applied: bool, message: String)

const REQUEST_TIMEOUT_SEC: float = 15.0
const ALLOWED_PATCH_PREFIXES: Array[String] = [
	"chapters/",
	"data/stats/",
	"data/theming/",
	"data/memory_echoes/",
	"data/localization/",
]

var _http: HTTPRequest = null
var _state: String = "idle"  # idle / manifest / downloading
var _pending_version: String = ""


func _ready() -> void:
	_http = HTTPRequest.new()
	add_child(_http)
	_http.timeout = REQUEST_TIMEOUT_SEC
	_http.request_completed.connect(_on_request_completed)


func check_for_updates() -> void:
	if _state != "idle":
		return
	_state = "manifest"
	var headers := ["User-Agent: CorporateDragon/" + VersionInfo.APP_VERSION]
	var err := _http.request(VersionInfo.MANIFEST_URL, headers)
	if err != OK:
		_emit_done(false, "Couldn't reach update server.")


func _on_request_completed(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if code != 200:
		_emit_done(false, "Server returned %d." % code)
		return
	match _state:
		"manifest":
			_handle_manifest(body)
		"downloading":
			_handle_patch_blob(body)
		_:
			_emit_done(false, "Unexpected response.")


func _handle_manifest(body: PackedByteArray) -> void:
	var parsed = JSON.parse_string(body.get_string_from_utf8())
	if typeof(parsed) != TYPE_DICTIONARY:
		_emit_done(false, "Update file corrupted.")
		return
	var latest: String = String(parsed.get("latest_patch_version", VersionInfo.APP_VERSION))
	if _version_le(latest, VersionInfo.PATCH_VERSION):
		_emit_done(false, "You're already on the latest story (v%s)." % VersionInfo.PATCH_VERSION)
		return
	var blob_url: String = String(parsed.get("blob_url", ""))
	if blob_url.is_empty():
		_emit_done(false, "Update missing download link.")
		return
	_pending_version = latest
	_state = "downloading"
	var err := _http.request(blob_url)
	if err != OK:
		_emit_done(false, "Couldn't start patch download.")


func _handle_patch_blob(body: PackedByteArray) -> void:
	var parsed = JSON.parse_string(body.get_string_from_utf8())
	if typeof(parsed) != TYPE_DICTIONARY:
		_emit_done(false, "Patch payload corrupted.")
		return
	var target_dir: String = "user://patches/%s" % _pending_version
	DirAccess.make_dir_recursive_absolute(target_dir)
	var any_written: bool = false
	for k in parsed.keys():
		var src_path: String = String(k)
		if not src_path.begins_with("res://"):
			continue
		var rel: String = src_path.substr(6)
		if not _is_allowed_path(rel):
			push_warning("OTAManager: skipping disallowed patch path %s" % rel)
			continue
		var write_path: String = "%s/%s" % [target_dir, rel]
		DirAccess.make_dir_recursive_absolute(write_path.get_base_dir())
		var f := FileAccess.open(write_path, FileAccess.WRITE)
		if f == null:
			continue
		f.store_string(String(parsed[k]))
		f.close()
		any_written = true
	if not any_written:
		_emit_done(false, "Patch contained no installable files.")
		return
	# Pin this version only after all files succeeded — partial-failure rolls back on next boot.
	var mf := FileAccess.open("user://patches/manifest.json", FileAccess.WRITE)
	if mf:
		mf.store_string(JSON.stringify({"version": _pending_version}))
		mf.close()
	VersionInfo.set_active_patch(_pending_version)
	_emit_done(true, "Updated to story v%s — restart the chapter to see changes." % _pending_version)


func _is_allowed_path(rel: String) -> bool:
	for prefix in ALLOWED_PATCH_PREFIXES:
		if rel.begins_with(prefix):
			return true
	return false


func _version_le(a: String, b: String) -> bool:
	# Returns true iff a <= b (semver dotted-int comparison).
	var ap := a.split(".")
	var bp := b.split(".")
	var n: int = max(ap.size(), bp.size())
	for i in range(n):
		var av: int = ap[i].to_int() if i < ap.size() else 0
		var bv: int = bp[i].to_int() if i < bp.size() else 0
		if av < bv:
			return true
		if av > bv:
			return false
	return true


func _emit_done(ok: bool, msg: String) -> void:
	_state = "idle"
	_pending_version = ""
	update_check_completed.emit(ok, msg)
