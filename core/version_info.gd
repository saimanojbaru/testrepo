extends Node
## VersionInfo — autoload. Source of truth for app + content versions.
## APP_VERSION is the APK build version (bumped at sideload).
## PATCH_VERSION is the currently-installed OTA patch version (defaults to
## APP_VERSION when no patch is installed). The OTAManager writes a small
## bookkeeping file under user://patches/manifest.json after a successful
## patch download; this autoload reads it at boot.

const APP_VERSION: String = "1.0.0"
const MANIFEST_URL: String = "https://saimanojbaru.github.io/testrepo/manifest.json"

var PATCH_VERSION: String = APP_VERSION
var current_patch: Dictionary = {}


func _ready() -> void:
	_load_current_patch()


func _load_current_patch() -> void:
	var path := "user://patches/manifest.json"
	if not FileAccess.file_exists(path):
		return
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return
	var parsed = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) == TYPE_DICTIONARY:
		current_patch = parsed
		PATCH_VERSION = String(parsed.get("version", APP_VERSION))


func set_active_patch(version: String) -> void:
	PATCH_VERSION = version
	current_patch = {"version": version}
