package com.trailerly.util

import android.util.Patterns

/**
 * Utility object for input validation, particularly for authentication forms.
 * Provides validation functions that follow Firebase Auth requirements.
 */
object ValidationUtils {

    /**
     * Validates if the provided email string is a valid email address.
     * Uses Android's built-in email pattern matcher.
     *
     * @param email The email string to validate
     * @return true if email is valid, false otherwise
     */
    fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Validates if the provided password meets minimum requirements.
     * Firebase Auth requires passwords to be at least 6 characters.
     *
     * @param password The password string to validate
     * @return true if password is valid, false otherwise
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    /**
     * Gets a user-friendly error message for email validation.
     *
     * @param email The email string to check
     * @return null if valid, error message string if invalid
     */
    fun getEmailError(email: String): String? {
        return if (isValidEmail(email)) null else "Please enter a valid email address"
    }

    /**
     * Gets a user-friendly error message for password validation.
     *
     * @param password The password string to check
     * @return null if valid, error message string if invalid
     */
    fun getPasswordError(password: String): String? {
        return if (isValidPassword(password)) null else "Password must be at least 6 characters"
    }

    /**
     * Validates that two password strings match.
     * Used for sign-up forms with password confirmation.
     *
     * @param password The original password
     * @param confirmPassword The confirmation password
     * @return true if passwords match, false otherwise
     */
    fun doPasswordsMatch(password: String, confirmPassword: String): Boolean {
        return password == confirmPassword
    }
}
