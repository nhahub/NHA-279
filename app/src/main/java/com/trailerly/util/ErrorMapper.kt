package com.trailerly.util

import retrofit2.HttpException
import java.io.IOException

/**
 * Utility object for mapping network exceptions to user-friendly error messages and determining retry behavior.
 * Centralizes error handling logic for consistent UX across the app.
 */
object ErrorMapper {

    /**
     * Maps network exceptions to user-friendly error messages.
     * Handles different types of network errors with appropriate messaging.
     *
     * @param e The exception to map
     * @return A user-friendly error message string
     */
    fun mapNetworkError(e: Exception): String {
        return when (e) {
            is IOException -> {
                when (e) {
                    is java.net.SocketTimeoutException -> "Request timed out. Please try again"
                    is java.net.UnknownHostException -> "No internet connection. Please check your network"
                    is java.net.ConnectException -> "Unable to connect. Please check your internet connection"
                    else -> "Network error. Please check your connection"
                }
            }
            is HttpException -> {
                when (e.code()) {
                    400 -> "Bad request. Please try again"
                    401 -> if (isApiKeyError(e)) "Invalid TMDb API key. Please check your configuration." else "Authentication failed. Please sign in again"
                    403 -> if (isApiKeyError(e)) "Invalid TMDb API key. Please check your configuration." else "Access denied. You don't have permission"
                    404 -> "Content not found"
                    408 -> "Request timed out. Please try again"
                    429 -> "Too many requests. Please wait a moment"
                    in 500..599 -> "Server error. Please try again later"
                    else -> "Server error: ${e.code()}"
                }
            }
            else -> "An unexpected error occurred. Please try again"
        }
    }

    private fun isApiKeyError(e: Exception): Boolean {
        if (e !is HttpException) return false
        if (e.code() != 401 && e.code() != 403) return false
        val message = e.message() ?: ""
        return message.contains("Invalid API key") || message.contains("api_key")
    }

    /**
     * Determines if an exception represents a retryable error.
     * Network errors and certain server errors can be retried.
     *
     * @param e The exception to check
     * @return true if the error is retryable, false otherwise
     */
    fun isRetryableError(e: Exception): Boolean {
        return when (e) {
            is IOException -> true
            is HttpException -> e.code() in listOf(408, 429) || e.code() in 500..599
            else -> false
        }
    }

    /**
     * Determines if a retry button should be shown for an error.
     * Similar to isRetryableError but may have different criteria for UI.
     *
     * @param e The exception to check
     * @return true if a retry button should be shown, false otherwise
     */
    fun shouldShowRetryButton(e: Exception): Boolean {
        return isRetryableError(e)
    }

    /**
     * Gets an appropriate icon resource for an error type.
     * Returns a drawable resource ID for error icons.
     *
     * @param e The exception to check
     * @return Resource ID for the error icon
     */
    fun getErrorIcon(e: Exception): Int {
        // This would return R.drawable.ic_error_network, etc.
        // For now, return a default - implement when icons are available
        return android.R.drawable.ic_dialog_alert
    }
}
