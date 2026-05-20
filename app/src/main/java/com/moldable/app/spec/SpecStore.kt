package com.moldable.app.spec

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

object SpecStore {
    private const val CURRENT = "spec_current.json"
    private const val PREVIOUS = "spec_previous.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun load(ctx: Context): AppSpec {
        val f = file(ctx, CURRENT)
        if (!f.exists()) return AppSpec()
        return runCatching { json.decodeFromString<AppSpec>(f.readText()) }
            .getOrDefault(AppSpec())
    }

    fun save(ctx: Context, spec: AppSpec) {
        val cur = file(ctx, CURRENT)
        if (cur.exists()) cur.copyTo(file(ctx, PREVIOUS), overwrite = true)
        cur.writeText(json.encodeToString(AppSpec.serializer(), spec))
    }

    fun revert(ctx: Context): AppSpec? {
        val prev = file(ctx, PREVIOUS)
        if (!prev.exists()) return null
        val text = prev.readText()
        prev.copyTo(file(ctx, CURRENT), overwrite = true)
        return runCatching { json.decodeFromString<AppSpec>(text) }.getOrNull()
    }

    fun reset(ctx: Context): AppSpec {
        val def = AppSpec()
        save(ctx, def)
        return def
    }

    fun toJson(spec: AppSpec): String = json.encodeToString(AppSpec.serializer(), spec)

    fun fromJson(text: String): AppSpec = json.decodeFromString(text)
}
