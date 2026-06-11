package com.hitit.app.ui.screens.moneyvibe

import androidx.compose.runtime.Composable

/**
 * Lite-flavor no-op for the auto-capture card. The notification listener that powers auto-capture is
 * a Play Protect stalkerware signal on sideloaded APKs, so the default (lite) download omits it
 * entirely and installs friction-free. Same FQN as the full implementation so MoneyVibeScreen
 * (shared) compiles unchanged; here it simply renders nothing. Auto-capture lives in the full build.
 */
@Composable
fun AutoCaptureCard() {
    // Intentionally empty in the lite distribution.
}
