package com.trailerly.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

/**
 * RetryInterceptor automatically retries failed network requests with exponential backoff.
 * Retries on IOException (network errors) and specific HTTP status codes (408 timeout, 429 rate limit, 500-599 server errors).
 * Does not retry on client errors (400-499 except 408 and 429) or successful responses.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 1000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(request)

                // Don't retry successful responses
                if (response.isSuccessful) {
                    return response
                }

                // Retry on specific server errors
                if (shouldRetryOnStatusCode(response.code)) {
                    response.close()
                    if (attempt < maxRetries) {
                        val delay = calculateDelay(attempt, response)
                        Thread.sleep(delay)
                        continue
                    }
                }

                // Don't retry on client errors (except 408, 429)
                return response

            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    Thread.sleep(calculateDelay(attempt))
                    continue
                }
            }
        }

        // If we get here, all retries failed
        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    private fun shouldRetryOnStatusCode(code: Int): Boolean {
        return when (code) {
            408, 429 -> true // Request Timeout, Too Many Requests
            in 500..599 -> true // Server errors
            else -> false
        }
    }

    private fun calculateDelay(attempt: Int, response: Response? = null): Long {
        // Check for Retry-After header on 429 responses
        val retryAfter = response?.header("Retry-After")?.toLongOrNull()
        val baseDelay = if (retryAfter != null && response.code == 429) {
            retryAfter * 1000L // Convert seconds to milliseconds
        } else {
            retryDelayMillis * (2.0.pow(attempt.toDouble())).toLong()
        }

        // Add jitter to avoid thundering herd
        val jitter = Random.nextLong(0, 251) // 0-250ms jitter
        val finalDelay = baseDelay + jitter

        // Cap at 30 seconds
        return minOf(finalDelay, 30_000L)
    }
}
