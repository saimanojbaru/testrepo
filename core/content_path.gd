class_name ContentPath
extends Object
## Resolves a res:// path against the active OTA patch. If a patched copy
## exists at user://patches/<version>/<rel>, returns that; otherwise returns
## the original res:// path. Static — call without instantiation.
##
## VersionInfo is an autoload; identifier resolves from any script context.

static func resolve(res_path: String) -> String:
	if not res_path.begins_with("res://"):
		return res_path
	var rel: String = res_path.substr(6)
	var patched: String = "user://patches/%s/%s" % [VersionInfo.PATCH_VERSION, rel]
	if FileAccess.file_exists(patched):
		return patched
	return res_path
