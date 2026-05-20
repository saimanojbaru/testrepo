package com.moldable.app.state

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class AppState(initial: Map<String, JsonElement> = emptyMap()) {
    private val backing = mutableStateMapOf<String, JsonElement>().apply { putAll(initial) }

    val asMap: Map<String, JsonElement> get() = backing

    fun get(key: String): JsonElement? = backing[key]

    fun getString(key: String): String = when (val v = backing[key]) {
        null, JsonNull -> ""
        is JsonPrimitive -> v.content
        else -> v.toString()
    }

    fun set(key: String, value: JsonElement) {
        backing[key] = value
    }

    fun setString(key: String, value: String) {
        backing[key] = JsonPrimitive(value)
    }

    fun reset(initial: Map<String, JsonElement>) {
        backing.clear()
        backing.putAll(initial)
    }
}
