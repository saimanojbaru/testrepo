package com.hitit.app.coach

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.hitit.app.data.local.AppPreferences
import com.hitit.domain.coach.CoachInsight
import com.hitit.domain.coach.CoachPrompt
import com.hitit.domain.coach.CoachRephraser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional on-device LLM rephraser (MediaPipe LLM Inference). Strictly additive and defensive:
 *  - Enabled only when the user turns it on AND points at a real model file (no bundled model,
 *    no INTERNET permission — preserves offline-first).
 *  - EVERY failure path (disabled, missing file, load error, inference error) returns null/false so
 *    the caller falls back to the always-working rule-based coach. The app can never break because
 *    of this layer.
 *
 * NOTE: this path is compile-verified only in CI; actual model loading/inference requires a capable
 * physical device with a model file present, which cannot be exercised here.
 */
@Singleton
class MediaPipeCoachRephraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : CoachRephraser {

    @Volatile
    private var engine: LlmInference? = null

    override suspend fun isReady(): Boolean = prefs.llmCoachEnabled && modelFile()?.exists() == true

    override suspend fun rephrase(insights: List<CoachInsight>): String? {
        if (!prefs.llmCoachEnabled) return null
        val model = modelFile()?.takeIf { it.exists() } ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val llm = ensureEngine(model.absolutePath)
                llm.generateResponse(CoachPrompt.build(insights))?.trim()?.ifBlank { null }
            }.getOrNull()
        }
    }

    private fun ensureEngine(modelPath: String): LlmInference {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            // topK/temperature sampling live on the session options in this version; the base
            // options just need the model path + token budget for a one-shot generate.
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()
            return LlmInference.createFromOptions(context, options).also { engine = it }
        }
    }

    private fun modelFile(): File? = prefs.llmModelPath?.let { File(it) }
}
