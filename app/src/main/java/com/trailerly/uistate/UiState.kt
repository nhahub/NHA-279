package com.trailerly.uistate

sealed class UiState<out T> {
    data class Loading<out T>(val data: T? = null) : UiState<T>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error<out T>(
        val message: String,
        val isNetworkError: Boolean = false,
        val canRetry: Boolean = true,
        val retryAction: (() -> Unit)? = null,
        val data: T? = null
    ) : UiState<T>()
}

// Helper extension to easily get data if available in any state
fun <T> UiState<T>.dataOrNull(): T? {
    return when (this) {
        is UiState.Success -> this.data
        is UiState.Loading -> this.data
        is UiState.Error -> this.data
    }
}
