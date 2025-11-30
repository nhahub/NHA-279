package com.trailerly.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.trailerly.BuildConfig
import com.trailerly.network.interceptor.ApiKeyInterceptor
import com.trailerly.network.interceptor.RetryInterceptor
import com.trailerly.util.TmdbConstants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor(maxRetries = 3, retryDelayMillis = 1000)) // Add retry interceptor before API key
        .addInterceptor(ApiKeyInterceptor()) // Add API key interceptor
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
        .readTimeout(30, TimeUnit.SECONDS) // Read timeout
        .writeTimeout(30, TimeUnit.SECONDS) // Write timeout
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(TmdbConstants.BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(httpClient)
        .build()

    val tmdbApiService: MovieApiService by lazy {
        retrofit.create(MovieApiService::class.java)
    }
}