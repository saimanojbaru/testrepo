package com.hitit.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import com.hitit.app.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    fun complete() {
        appPreferences.onboardingComplete = true
    }
}
