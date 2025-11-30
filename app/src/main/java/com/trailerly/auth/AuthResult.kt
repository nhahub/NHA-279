package com.trailerly.auth

/**
 * Sealed class representing the result of authentication operations.
 * Used for one-time operations like sign in, sign up, and sign out.
 */
sealed class AuthResult {
    /**
     * Operation completed successfully.
     */
    object Success : AuthResult()

    /**
     * Operation failed with an error.
     * @param message User-friendly error message
     */
    data class Error(val message: String) : AuthResult()

    /**
     * Operation is in progress.
     */
    object Loading : AuthResult()
}
