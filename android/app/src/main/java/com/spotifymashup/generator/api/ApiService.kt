package com.spotifymashup.generator.api

import com.spotifymashup.generator.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

interface MashupApiService {

    @POST("api/mashup")
    suspend fun createMashup(@Body request: MashupRequest): JobResponse

    @GET("api/mashup/{jobId}")
    suspend fun getJob(@Path("jobId") jobId: String): JobResponse

    /** Streams the MP3 bytes so we can write them to disk without buffering the whole file. */
    @Streaming
    @GET("api/mashup/{jobId}/download")
    suspend fun downloadMashup(@Path("jobId") jobId: String): Response<ResponseBody>
}

// ── Singleton factory ─────────────────────────────────────────────────────────

object ApiClient {

    val service: MashupApiService by lazy { buildService() }

    private fun buildService(): MashupApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // long timeout for download
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MashupApiService::class.java)
    }
}
