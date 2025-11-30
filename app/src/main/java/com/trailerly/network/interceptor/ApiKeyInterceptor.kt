package com.trailerly.network.interceptor

import com.trailerly.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that automatically appends TMDB API key to all requests.
 * Should be added to OkHttpClient before other interceptors.
 */
class ApiKeyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Add api_key query parameter
        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
