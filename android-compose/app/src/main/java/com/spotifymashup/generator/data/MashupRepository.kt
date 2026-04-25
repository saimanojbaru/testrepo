package com.spotifymashup.generator.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class MashupRepository(initialBaseUrl: String = "http://10.0.2.2:8000") {

    var baseUrl: String = initialBaseUrl
        set(value) {
            field = value.trimEnd('/')
            api = buildApi(field)
        }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun buildApi(url: String): MashupApi {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
        return Retrofit.Builder()
            .baseUrl("${url.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MashupApi::class.java)
    }

    private var api: MashupApi = buildApi(initialBaseUrl)

    suspend fun trendingHook(spotifyUrl: String, topK: Int = 5) =
        api.trendingHook(TrendingHookRequest(spotifyUrl, topK))

    suspend fun compatibility(a: String, b: String) =
        api.compatibility(CompatibilityRequest(a, b))

    suspend fun createMashup(req: MashupRequest) = api.createMashup(req)
    suspend fun pollJob(jobId: String) = api.getJob(jobId)

    fun downloadUrl(jobId: String) = "$baseUrl/api/mashup/$jobId/download"
}
