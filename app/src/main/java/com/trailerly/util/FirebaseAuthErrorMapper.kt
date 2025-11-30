package com.trailerly.util

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException

/**
 * Utility object for mapping Firebase Authentication exceptions to user-friendly error messages.
 * Centralizes error message mapping for consistent UX across the app.
 */
object FirebaseAuthErrorMapper {

    /**
     * Maps Firebase Auth exceptions to user-friendly error messages.
     *
     * @param exception The Firebase Auth exception to map
     * @return A user-friendly error message string
     */
    fun mapFirebaseAuthError(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseAuthUserCollisionException -> "An account with this email already exists"
            is FirebaseAuthInvalidUserException -> "No account found with this email"
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use a stronger password"
            is FirebaseNetworkException -> "Network error. Please check your connection"
            else -> "Authentication failed. Please try again"
        }
    }
}
