package com.moldable.app.js

import com.moldable.app.state.AppState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.mozilla.javascript.Context as JsContext
import org.mozilla.javascript.Function as JsFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

object JsRunner {
    private val jsonCodec = Json { ignoreUnknownKeys = true }

    fun run(
        script: String,
        appState: AppState,
        onToast: (String) -> Unit,
        onNavigate: (String) -> Unit
    ) {
        val cx = JsContext.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = JsContext.VERSION_ES6
            val scope = cx.initStandardObjects()

            val getFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    val k = args?.getOrNull(0)?.toString() ?: return null
                    val el = appState.get(k) ?: return null
                    return elementToJs(el)
                }
            }
            val setFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    val k = args?.getOrNull(0)?.toString() ?: return null
                    val v = args.getOrNull(1)
                    appState.set(k, jsToElement(v))
                    return Undefined.instance
                }
            }
            val pushFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    val k = args?.getOrNull(0)?.toString() ?: return null
                    val v = args.getOrNull(1)
                    val cur = appState.get(k)
                    val arr = if (cur is JsonArray) cur else JsonArray(emptyList())
                    val next = buildJsonArray {
                        arr.forEach { add(it) }
                        add(jsToElement(v))
                    }
                    appState.set(k, next)
                    return Undefined.instance
                }
            }
            val removeAtFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    val k = args?.getOrNull(0)?.toString() ?: return null
                    val idx = (args.getOrNull(1) as? Number)?.toInt() ?: return null
                    val cur = appState.get(k) as? JsonArray ?: return null
                    val next = buildJsonArray {
                        cur.forEachIndexed { i, v -> if (i != idx) add(v) }
                    }
                    appState.set(k, next)
                    return Undefined.instance
                }
            }
            val toastFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    onToast(args?.getOrNull(0)?.toString().orEmpty())
                    return Undefined.instance
                }
            }
            val navFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: JsContext, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>?
                ): Any? {
                    onNavigate(args?.getOrNull(0)?.toString().orEmpty())
                    return Undefined.instance
                }
            }

            ScriptableObject.putProperty(scope, "get", getFn)
            ScriptableObject.putProperty(scope, "set", setFn)
            ScriptableObject.putProperty(scope, "push", pushFn)
            ScriptableObject.putProperty(scope, "removeAt", removeAtFn)
            ScriptableObject.putProperty(scope, "toast", toastFn)
            ScriptableObject.putProperty(scope, "navigate", navFn)

            cx.evaluateString(scope, script, "user-script", 1, null)
        } catch (t: Throwable) {
            onToast("Script error: ${t.message}")
        } finally {
            JsContext.exit()
        }
    }

    private fun elementToJs(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.contentOrNull
        is JsonArray -> e.map { elementToJs(it) }.toTypedArray()
        is JsonObject -> e.mapValues { elementToJs(it.value) }
    }

    private fun jsToElement(v: Any?): JsonElement = when (v) {
        null, Undefined.instance -> JsonNull
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is NativeArray -> buildJsonArray {
            for (i in 0 until v.length.toInt()) add(jsToElement(v[i]))
        }
        is NativeObject -> {
            val obj = mutableMapOf<String, JsonElement>()
            for (id in v.ids) {
                val key = id.toString()
                obj[key] = jsToElement(v.get(key, v))
            }
            JsonObject(obj)
        }
        else -> JsonPrimitive(v.toString())
    }
}
