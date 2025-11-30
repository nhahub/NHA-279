package com.trailerly.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.trailerly.BuildConfig
import com.trailerly.network.interceptor.RetryInterceptor
import com.trailerly.util.TmdbConstants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A singleton Retrofit client for the YouTube Data API.
 *
 * This client is configured specifically for the YouTube API, with its own base URL and
 * authentication mechanism (API key as a query parameter, handled in [YouTubeApiService]).
 * It reuses the logging and retry interceptors from the main TMDB API client for consistency.
 */
object YouTubeApiClient {

    // Moshi instance for JSON parsing, configured for Kotlin
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // OkHttpClient with logging and retry interceptors
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(RetryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit instance configured for the YouTube API
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TmdbConstants.YOUTUBE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Lazily created instance of the [YouTubeApiService].
     */
    val youtubeApiService: YouTubeApiService by lazy {
        retrofit.create(YouTubeApiService::class.java)
    }
}