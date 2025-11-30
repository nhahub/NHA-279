package com.trailerly.auth

/**
 * Sealed class representing the authentication state of the user.
 * Used for reactive state management in the UI layer.
 */
sealed class AuthState {
    /**
     * Initial loading state while checking authentication status.
     */
    object Loading : AuthState()

    /**
     * User is authenticated with Firebase.
     * @param userId Unique Firebase user ID
     * @param email User's email address (null for anonymous users)
     * @param displayName User's display name (null for anonymous users)
     * @param isAnonymous True if user is signed in anonymously (guest mode)
     * @param photoUrl User's profile picture URL (null if no photo set)
     */
    data class Authenticated(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val isAnonymous: Boolean,
        val photoUrl: String? = null
    ) : AuthState()

    /**
     * User is not authenticated.
     */
    object Unauthenticated : AuthState()

    /**
     * Authentication error occurred.
     * @param message User-friendly error message
     */
    data class Error(val message: String) : AuthState()
}
