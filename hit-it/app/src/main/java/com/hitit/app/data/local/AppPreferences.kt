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

    private companion object {
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_DEMO_SEEDED = "demo_seeded"
    }
}
