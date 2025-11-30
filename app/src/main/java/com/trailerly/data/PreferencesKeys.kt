package com.trailerly.data

import androidx.datastore.preferences.core.*

/**
 * Centralized preference keys used by PreferencesRepository to access DataStore values.
 * This improves maintainability and prevents typos.
 *
 * VERSIONING STRATEGY:
 * DataStore uses versioning for schema evolution. Increment CURRENT_VERSION whenever:
 * - Adding new preference keys
 * - Removing preference keys
 * - Changing key types (e.g., String to Int)
 * - Changing key names
 *
 * Migration happens automatically on first DataStore access via PreferencesRepository.ensureMigrationComplete().
 *
 * Version 1: Initial schema with 5 keys
 */
object PreferencesKeys {
    val DATASTORE_VERSION = intPreferencesKey("datastore_version")
    val SAVED_MOVIE_IDS = stringSetPreferencesKey("saved_movie_ids")
    val DARK_MODE = booleanPreferencesKey("dark_mode")

    val IS_GUEST_USER = booleanPreferencesKey("is_guest_user")
    val USER_NAME = stringPreferencesKey("user_name")
    val USER_EMAIL = stringPreferencesKey("user_email")
    val SHOW_ENGLISH_TRANSLATION = booleanPreferencesKey("show_english_translation")
    val FIRESTORE_SYNC_PENDING = booleanPreferencesKey("firestore_sync_pending")
    val FIRESTORE_PREFERENCES_SYNC_PENDING = booleanPreferencesKey("firestore_preferences_sync_pending")
    val TRAILER_QUALITY = stringPreferencesKey("trailer_quality")
}