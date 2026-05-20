package com.moldable.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun moldSpec(currentSpecJson: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        val system = SYSTEM_PROMPT
        val userMsg = "Current AppSpec JSON:\n```json\n$currentSpecJson\n```\n\n" +
                "User instruction:\n$userPrompt\n\n" +
                "Return ONLY the new AppSpec as a single JSON object inside a ```json``` fenced block. " +
                "Do not include any explanation outside the fence."

        val body = buildJsonObject {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 4096)
            put("system", system)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMsg)
                })
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("Claude API ${resp.code}: ${text.take(400)}")
            }
            val root = json.parseToJsonElement(text).jsonObject
            val content = root["content"]?.jsonArray ?: throw RuntimeException("No content in response")
            val combined = StringBuilder()
            content.forEach { item ->
                val obj = item.jsonObject
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    combined.append(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }
            val out = combined.toString()
            extractJson(out) ?: throw RuntimeException("No JSON block in response:\n${out.take(400)}")
        }
    }

    private fun extractJson(text: String): String? {
        val fence = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```")
        fence.find(text)?.let { return it.groupValues[1] }
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are the configuration engine for a self-molding Android app.

            The app renders a JSON document called AppSpec into Jetpack Compose UI at runtime.
            Your job: take the current AppSpec and a user instruction, and return a NEW AppSpec
            that fulfils the instruction while remaining a valid, renderable document.

            AppSpec schema (be strict):
            {
              "version": int,
              "title": string,
              "theme": { "mode": "light"|"dark"|"system",
                         "primary": "#RRGGBB", "background": "#RRGGBB",
                         "surface": "#RRGGBB", "onPrimary": "#RRGGBB", "onBackground": "#RRGGBB" },
              "initialScreen": string,                       // must match a screen id
              "state": { "<key>": <any JSON value> },        // initial in-memory state
              "screens": [
                {
                  "id": string,
                  "title": string,
                  "widgets": [ Widget, ... ]
                }
              ]
            }

            Widget kinds (use the "type" field):
              - "text"        : { type, text }
              - "heading"     : { type, text }
              - "spacer"      : { type }
              - "input"       : { type, placeholder, bind }                  // bind = state key
              - "button"      : { type, text, onClick: Action }
              - "list"        : { type, itemsFrom }                           // itemsFrom = state key holding array of strings
              - "checkbox"    : { type, text, bind }
              - "row" | "col" : { type, children: [Widget, ...] }

            Action kinds (use the "kind" field):
              - "navigate"    : { kind, target }                              // target = screen id
              - "setState"    : { kind, key, value }                          // any JSON value
              - "appendList"  : { kind, key, value }                          // appends value (any) into state[key] (array)
              - "removeAt"    : { kind, key, value }                          // removes index `value` from state[key] (array)
              - "toggle"      : { kind, key }                                 // boolean toggle on state[key]
              - "runScript"   : { kind, script }                              // tiny JS body, has helpers: get(k), set(k,v), push(k,v), removeAt(k,i), toast(s), navigate(id)
              - "toast"       : { kind, message }

            Rules:
            - Output ONLY a single JSON object that is the complete new AppSpec.
            - Preserve user data in `state` whenever possible.
            - Pick reasonable colors; mode "system" is fine.
            - Keep it self-consistent: every `target` must reference an existing screen id;
              every `bind`/`itemsFrom`/`key` should make sense for its action.
            - Do not invent widget types or action kinds outside the list above.
            - No commentary, no markdown outside the fence.
        """.trimIndent()
    }
}
