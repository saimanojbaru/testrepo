package com.hitit.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight app-level flags backed by SharedPreferences (no extra dependency). */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("hitit_prefs", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** True once we've seeded (or deliberately skipped) the first-launch demo data. */
    var demoSeeded: Boolean
        get() = prefs.getBoolean(KEY_DEMO_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_SEEDED, value).apply()

    /** True once we've asked for POST_NOTIFICATIONS (after the first logged hit) so we never re-prompt. */
    var notifPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, value).apply()

    /** Whether the user opted in to the on-device LLM coach (off by default — rule-based always works). */
    var llmCoachEnabled: Boolean
        get() = prefs.getBoolean(KEY_LLM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LLM_ENABLED, value).apply()

    /** Absolute path to a user-supplied .task/.litertlm model file (null until set). */
    var llmModelPath: String?
        get() = prefs.getString(KEY_LLM_MODEL_PATH, null)
        set(value) = prefs.edit().putString(KEY_LLM_MODEL_PATH, value).apply()

    /** Weekly "Burner Budget" (fun-money cap) in paise; 0 = off. */
    var burnerBudgetPaise: Long
        get() = prefs.getLong(KEY_BURNER_BUDGET, 0L)
        set(value) = prefs.edit().putLong(KEY_BURNER_BUDGET, value).apply()

    private companion object {
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_DEMO_SEEDED = "demo_seeded"
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
        const val KEY_LLM_ENABLED = "llm_coach_enabled"
        const val KEY_LLM_MODEL_PATH = "llm_model_path"
        const val KEY_BURNER_BUDGET = "burner_budget_paise"
    }
}
