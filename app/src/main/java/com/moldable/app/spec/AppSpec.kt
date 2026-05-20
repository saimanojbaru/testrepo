package com.moldable.app.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AppSpec(
    val version: Int = 1,
    val title: String = "Moldable",
    val theme: Theme = Theme(),
    val initialScreen: String = "home",
    val state: Map<String, JsonElement> = emptyMap(),
    val screens: List<Screen> = listOf(
        Screen(
            id = "home",
            title = "Welcome",
            widgets = listOf(
                Widget(type = "text", text = "Tap the prompt button to mold this app."),
                Widget(type = "text", text = "Try: \"turn this into a todo list\" or \"add a counter\".")
            )
        )
    )
)

@Serializable
data class Theme(
    val mode: String = "system",
    val primary: String = "#5B8DEF",
    val background: String = "#0D1B2A",
    val surface: String = "#152339",
    val onPrimary: String = "#FFFFFF",
    val onBackground: String = "#E6EDF7"
)

@Serializable
data class Screen(
    val id: String,
    val title: String = "",
    val widgets: List<Widget> = emptyList()
)

@Serializable
data class Widget(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val placeholder: String? = null,
    val bind: String? = null,
    val items: List<String>? = null,
    val itemsFrom: String? = null,
    val onClick: Action? = null,
    val children: List<Widget>? = null
)

@Serializable
data class Action(
    val kind: String,
    val target: String? = null,
    val key: String? = null,
    val value: JsonElement? = null,
    val script: String? = null,
    val message: String? = null
)
