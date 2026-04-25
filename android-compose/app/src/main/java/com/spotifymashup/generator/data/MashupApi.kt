package com.spotifymashup.generator.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MashupApi {
    @GET("/health")
    suspend fun health(): Map<String, String>

    @POST("/api/trending-hook")
    suspend fun trendingHook(@Body req: TrendingHookRequest): TrendingHookResponse

    @POST("/api/compatibility")
    suspend fun compatibility(@Body req: CompatibilityRequest): CompatibilityResponse

    @POST("/api/mashup")
    suspend fun createMashup(@Body req: MashupRequest): JobResponse

    @GET("/api/mashup/{jobId}")
    suspend fun getJob(@Path("jobId") jobId: String): JobResponse
}
