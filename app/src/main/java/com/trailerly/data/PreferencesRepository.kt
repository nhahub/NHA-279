package com.trailerly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trailerly.repository.AuthRepository
import com.trailerly.util.dataStorePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

const val CURRENT_VERSION = 5

/**
 * Repository class that encapsulates all DataStore operations for local data persistence.
 * Provides type-safe access to user preferences and app state.
 *
 * VERSIONING & MIGRATION:
 * This repository uses versioning to handle DataStore schema evolution. Migration runs
 * automatically on first DataStore access via ensureMigrationComplete(). See PreferencesKeys
 * for versioning strategy and migration history.
 *
 * ADDING NEW PREFERENCES:
 * 1. Add new key to PreferencesKeys
 * 2. Increment PreferencesKeys.CURRENT_VERSION
 * 3. Create new migration method (e.g., migrateV1ToV2)
 * 4. Update performMigration() to call new migration method
 * 5. Document changes in PreferencesKeys migration history
 * 6. Add getter/setter methods in this class
 * 7. Test migration with existing user data
 *
 * REMOVING PREFERENCES:
 * 1. Increment PreferencesKeys.CURRENT_VERSION
 * 2. Create migration method that removes old key
 * 3. Update performMigration()
 * 4. Document in migration history
 * 5. Remove getter/setter methods (or deprecate first)
 *
 * CHANGING KEY TYPES:
 * 1. Increment PreferencesKeys.CURRENT_VERSION
 * 2. Add new key with new type
 * 3. Create migration method that:
 *    - Reads old key
 *    - Transforms data
 *    - Writes to new key
 *    - Removes old key
 * 4. Update all references to use new key
 * 5. Document in migration history
 */
class PreferencesRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) {

    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val dataStore = context.dataStorePreferences
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    // Migration state tracking
    private var isMigrationComplete = false
    private val migrationMutex = Mutex()

    /**
     * Ensures DataStore migration is complete before any operations.
     * This method runs automatically on first DataStore access and handles
     * schema evolution from previous app versions.
     */
    private suspend fun ensureMigrationComplete() {
        // Early return if migration already completed
        if (isMigrationComplete) return

        migrationMutex.withLock {
            // Double-check after acquiring lock (double-checked locking pattern)
            if (isMigrationComplete) return

            try {
                // Read current preferences to check version
                val preferences = dataStore.data.first()
                val currentVersion = preferences[PreferencesKeys.DATASTORE_VERSION] ?: 0

                crashlytics.setCustomKey("datastore_version_before", currentVersion)
                crashlytics.setCustomKey("migration_needed", currentVersion != CURRENT_VERSION)

                // Determine migration path
                when {
                    currentVersion == 0 -> {
                        // Version 0: Either first install or existing user upgrading
                        // Replaced preferences.asMap().isNotEmpty() with a check for a common key
                        val hasExistingData = preferences.contains(PreferencesKeys.SAVED_MOVIE_IDS) ||
                                              preferences.contains(PreferencesKeys.DARK_MODE) ||
                                              preferences.contains(PreferencesKeys.IS_GUEST_USER) ||
                                              preferences.contains(PreferencesKeys.USER_NAME) ||
                                              preferences.contains(PreferencesKeys.USER_EMAIL)


                        if (hasExistingData) {
                            // Existing user with data - apply migration
                            performMigration(0, CURRENT_VERSION)
                            crashlytics.log("Migrated existing user from v0 to v${CURRENT_VERSION}")
                        } else {
                            // New user - just set version
                            dataStore.edit { it[PreferencesKeys.DATASTORE_VERSION] = CURRENT_VERSION }
                            crashlytics.log("First install, set version to $CURRENT_VERSION")
                        }
                    }
                    currentVersion < CURRENT_VERSION -> {
                        // Upgrade needed
                        performMigration(currentVersion, CURRENT_VERSION)
                        crashlytics.log("DataStore upgraded from v$currentVersion to v${CURRENT_VERSION}")
                    }
                    currentVersion > CURRENT_VERSION -> {
                        // Version downgrade detected (user installed older app)
                        crashlytics.log("Version downgrade detected: v$currentVersion -> v${CURRENT_VERSION}")
                        crashlytics.setCustomKey("version_downgrade", true)
                        // Keep existing data, just update version to current
                        dataStore.edit { it[PreferencesKeys.DATASTORE_VERSION] = CURRENT_VERSION }
                    }
                    else -> {
                        // Already at current version
                        crashlytics.log("DataStore already at current version v${CURRENT_VERSION}")
                    }
                }

                crashlytics.setCustomKey("datastore_version_after", CURRENT_VERSION)
                crashlytics.setCustomKey("migration_success", true)
                crashlytics.setCustomKey("migration_timestamp", System.currentTimeMillis())

            } catch (e: Exception) {
                crashlytics.setCustomKey("migration_success", false)
                crashlytics.setCustomKey("migration_error", e.message ?: "Unknown")
                crashlytics.recordException(Exception("DataStore migration failed", e))
                // Attempt recovery for IOException
                if (e is java.io.IOException) {
                    try {
                        dataStore.edit { it.clear() }
                        dataStore.edit { it[PreferencesKeys.DATASTORE_VERSION] = CURRENT_VERSION }
                        crashlytics.log("Recovered from DataStore corruption during migration")
                        // Retry version check once
                        val retryPreferences = dataStore.data.first()
                        val retryVersion = retryPreferences[PreferencesKeys.DATASTORE_VERSION] ?: 0
                        if (retryVersion == CURRENT_VERSION) {
                            crashlytics.setCustomKey("migration_success", true)
                            isMigrationComplete = true
                            return
                        }
                    } catch (recoveryException: Exception) {
                        crashlytics.recordException(Exception("Failed to recover DataStore", recoveryException))
                    }
                }
                throw e // Re-throw to prevent corrupted state
            }

            isMigrationComplete = true
        }
    }

    /**
     * Performs migration from one version to another by applying all necessary
     * migration steps in sequence.
     */
    private suspend fun performMigration(fromVersion: Int, toVersion: Int) {
        for (version in fromVersion until toVersion) {
            when (version) {
                0 -> migrateV0ToV1()
                1 -> migrateV1ToV2()
                2 -> migrateV2ToV3()
                3 -> migrateV3ToV4()
                4 -> migrateV4ToV5()
                // Future migrations: 5 -> migrateV5ToV6(), etc.
            }
            crashlytics.log("Applied migration: v$version -> v${version + 1}")
        }
    }

    /**
     * Migrates from version 0 (pre-versioning) to version 1.
     * Handles existing users upgrading from apps without versioning.
     */
    private suspend fun migrateV0ToV1() {
        dataStore.edit { preferences ->
            // Validate existing SAVED_MOVIE_IDS data
            val savedMovieIds = preferences[PreferencesKeys.SAVED_MOVIE_IDS]
            if (savedMovieIds != null) {
                // Filter out invalid entries (non-integer strings)
                val validIds = savedMovieIds.mapNotNull { it.toIntOrNull() }
                if (validIds.size != savedMovieIds.size) {
                    // Some entries were invalid - clean them up
                    val invalidCount = savedMovieIds.size - validIds.size
                    crashlytics.log("Cleaned up $invalidCount invalid movie IDs during migration")
                    preferences[PreferencesKeys.SAVED_MOVIE_IDS] = validIds.map { it.toString() }.toSet()
                }
            }

            // Set version to 1
            preferences[PreferencesKeys.DATASTORE_VERSION] = 1
        }

        crashlytics.setCustomKey("last_migration", "v0_to_v1")
        crashlytics.log("Completed migration v0 -> v1")
    }

    /**
     * Migrates from version 1 to version 2.
     * Adds SHOW_ENGLISH_TRANSLATION key for language toggle feature.
     */
    private suspend fun migrateV1ToV2() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATASTORE_VERSION] = 2
        }
        crashlytics.setCustomKey("last_migration", "v1_to_v2")
        crashlytics.log("Completed migration v1 -> v2")
    }

    /**
     * Migrates from version 2 to version 3.
     * Adds FIRESTORE_SYNC_PENDING key for cross-device sync.
     */
    private suspend fun migrateV2ToV3() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATASTORE_VERSION] = 3
        }
        crashlytics.setCustomKey("last_migration", "v2_to_v3")
        crashlytics.log("Completed migration v2 -> v3")
    }

    /**
     * Migrates from version 3 to version 4.
     * Adds TRAILER_QUALITY key for trailer quality preference.
     */
    private suspend fun migrateV3ToV4() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATASTORE_VERSION] = 4
        }
        crashlytics.setCustomKey("last_migration", "v3_to_v4")
        crashlytics.log("Completed migration v3 -> v4")
    }

    /**
     * Migration v4 -> v5
     * Adds FIRESTORE_PREFERENCES_SYNC_PENDING key for cross-device preferences sync
     * Default value: false (no pending sync)
     * Migration: No data transformation needed, new key defaults to false
     * Purpose: Tracks when local preference changes (dark mode, language toggle) need to be pushed to Firestore after coming online
     */
    private suspend fun migrateV4ToV5() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATASTORE_VERSION] = 5
        }
        crashlytics.setCustomKey("last_migration", "v4_to_v5")
        crashlytics.log("Completed migration v4 -> v5")
    }

    /**
     * Handles corrupted DataStore by clearing data and resetting to defaults.
     * This provides a recovery mechanism when DataStore becomes unreadable.
     */
    private suspend fun handleCorruptedPreferences(exception: Throwable): androidx.datastore.preferences.core.Preferences {
        crashlytics.recordException(Exception("DataStore corrupted", exception))
        crashlytics.setCustomKey("datastore_corrupted", true)
        crashlytics.setCustomKey("corruption_type", exception::class.simpleName ?: "Unknown")

        try {
            // Attempt to clear corrupted data
            dataStore.edit { it.clear() }
            // Reset version to current
            dataStore.edit { it[PreferencesKeys.DATASTORE_VERSION] = CURRENT_VERSION }
            crashlytics.log("Cleared corrupted DataStore and reset to version $CURRENT_VERSION")
        } catch (clearException: Exception) {
            crashlytics.recordException(Exception("Failed to clear corrupted DataStore", clearException))
            // If clearing fails, continue with empty preferences
        }

        return emptyPreferences()
    }

    // Removed save/remove movie methods - now handled directly by Firestore

    // Removed getSavedMovieIds - now handled by Firestore real-time listener

    /**
     * Saves the dark mode preference.
     * @param enabled Whether dark mode is enabled
     * @param skipFirestoreSync Whether to skip syncing to Firestore (used during real-time updates)
     */
    suspend fun setDarkMode(enabled: Boolean, skipFirestoreSync: Boolean = false) {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.DARK_MODE] = enabled
            }
            if (!skipFirestoreSync) {
                repositoryScope.launch {
                    syncPreferencesToFirestore()
                }
            }
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to set dark mode: $enabled", e))
            throw e
        }
    }

    /**
     * Returns a Flow of the dark mode preference state.
     * @return Flow emitting the current dark mode state (defaults to true)
     */
    fun getDarkMode(): Flow<Boolean> {
        return dataStore.data
            .onStart { ensureMigrationComplete() }
            .catch { exception -> emit(handleCorruptedPreferences(exception)) }
            .map { preferences ->
                preferences[PreferencesKeys.DARK_MODE] ?: true
            }
    }



    /**
     * Saves the guest user state.
     * @param isGuest Whether the user is a guest
     */
    suspend fun setGuestUser(isGuest: Boolean) {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.IS_GUEST_USER] = isGuest
            }
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to set guest user: $isGuest", e))
            throw e
        }
    }

    /**
     * Returns a Flow of the guest user state.
     * @return Flow emitting whether the user is a guest (defaults to false)
     */
    fun isGuestUser(): Flow<Boolean> {
        return dataStore.data
            .onStart { ensureMigrationComplete() }
            .catch { exception -> emit(handleCorruptedPreferences(exception)) }
            .map { preferences ->
                preferences[PreferencesKeys.IS_GUEST_USER] ?: false
            }
    }

    /**
     * Saves user information (name and email).
     * @param name The user's name
     * @param email The user's email
     */
    suspend fun saveUserInfo(name: String, email: String) {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.USER_NAME] = name
                preferences[PreferencesKeys.USER_EMAIL] = email
            }
            crashlytics.log("Successfully saved user info")
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("last_datastore_operation", "save_user_info")
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to save user info", e))
            throw e
        }
    }

    /**
     * Returns a Flow of user information as a Pair of name and email.
     * @return Flow emitting Pair<String?, String?> containing user name and email
     */
    fun getUserInfo(): Flow<Pair<String?, String?>> {
        return dataStore.data
            .onStart { ensureMigrationComplete() }
            .catch { exception -> emit(handleCorruptedPreferences(exception)) }
            .map { preferences ->
                Pair(
                    preferences[PreferencesKeys.USER_NAME],
                    preferences[PreferencesKeys.USER_EMAIL]
                )
            }
    }

    /**
     * Clears all user data from DataStore (used during logout).
     * Removes user name, email, and guest status.
     * Note: Saved movies are now handled by Firestore, not DataStore.
     */
    suspend fun clearUserData() {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                preferences.remove(PreferencesKeys.USER_NAME)
                preferences.remove(PreferencesKeys.USER_EMAIL)
                preferences.remove(PreferencesKeys.IS_GUEST_USER)
                // Note: No longer removing SAVED_MOVIE_IDS as it's handled by Firestore
            }
            crashlytics.log("Successfully cleared user data")
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to clear user data", e))
            throw e
        }
    }

    /**
     * Saves the show English translation preference.
     * @param enabled Whether to show English translation
     * @param skipFirestoreSync Whether to skip syncing to Firestore (used during real-time updates)
     */
    suspend fun setShowEnglishTranslation(enabled: Boolean, skipFirestoreSync: Boolean = false) {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.SHOW_ENGLISH_TRANSLATION] = enabled
            }
            if (!skipFirestoreSync) {
                repositoryScope.launch {
                    syncPreferencesToFirestore()
                }
            }
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to set show English translation: $enabled", e))
            throw e
        }
    }

    /**
     * Returns a Flow of the show English translation preference state.
     * @return Flow emitting the current show English translation state (defaults to false)
     */
    fun getShowEnglishTranslation(): Flow<Boolean> {
        return dataStore.data
            .onStart { ensureMigrationComplete() }
            .catch { exception -> emit(handleCorruptedPreferences(exception)) }
            .map { preferences ->
                preferences[PreferencesKeys.SHOW_ENGLISH_TRANSLATION] ?: false
            }
    }

    /**
     * Saves the trailer quality preference.
     * @param quality The preferred trailer quality (e.g., "720p", "1080p", "auto")
     */
    suspend fun setTrailerQuality(quality: String?) {
        ensureMigrationComplete()
        try {
            dataStore.edit { preferences ->
                if (quality != null) {
                    preferences[PreferencesKeys.TRAILER_QUALITY] = quality
                } else {
                    preferences.remove(PreferencesKeys.TRAILER_QUALITY)
                }
            }
            crashlytics.setCustomKey("datastore_healthy", true)
        } catch (e: Exception) {
            crashlytics.setCustomKey("datastore_healthy", false)
            crashlytics.recordException(Exception("Failed to set trailer quality: $quality", e))
            throw e
        }
    }

    /**
     * Returns a Flow of the trailer quality preference state.
     * @return Flow emitting the current trailer quality state (defaults to null for auto)
     */
    fun getTrailerQuality(): Flow<String?> {
        return dataStore.data
            .onStart { ensureMigrationComplete() }
            .catch { exception -> emit(handleCorruptedPreferences(exception)) }
            .map { preferences ->
                preferences[PreferencesKeys.TRAILER_QUALITY]
            }
    }

    // Removed fetchAndMergeSavedMoviesFromFirestore - now handled by Firestore real-time listener

    /**
     * Fetches preferences from Firestore and merges them with local preferences.
     * Used when user logs in to sync preferences across devices.
     *
     * @param userId The user ID to fetch preferences for
     * @return Result indicating success or failure of the operation
     */
    suspend fun fetchAndMergePreferencesFromFirestore(userId: String): Result<Unit> {
        return try {
            ensureMigrationComplete()

            // Read local preferences and pending flag
            val localPreferences = dataStore.data.first()
            val isPendingSync = localPreferences[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] ?: false

            // Fetch cloud preferences
            val cloudResult = authRepository.getPreferencesFromFirestore(userId)
            if (cloudResult.isFailure) {
                val exception = cloudResult.exceptionOrNull()
                Timber.e("Failed to fetch preferences from Firestore: ${exception?.message}", exception)
                return Result.failure(exception ?: Exception("Unknown error"))
            }

            val cloudPreferences = cloudResult.getOrNull() ?: emptyMap()
            
            if (isPendingSync) {
                // If we have pending local changes, try to push them first
                Timber.d("Pending preferences sync detected. Attempting to push local changes...")
                val syncResult = syncPreferencesToFirestore()
                
                if (syncResult.isSuccess) {
                    // Sync succeeded, re-read cloud preferences after pushing local changes
                    val updatedCloudResult = authRepository.getPreferencesFromFirestore(userId)
                    if (updatedCloudResult.isSuccess) {
                        val updatedCloudPreferences = updatedCloudResult.getOrNull() ?: emptyMap()
                        
                        // Apply cloud preferences as source of truth
                        applyCloudPreferences(updatedCloudPreferences)
                        // Clear pending flag
                        dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = false }
                    } else {
                        // If re-fetch failed, apply original cloud preferences
                        applyCloudPreferences(cloudPreferences)
                        // Clear pending flag since we successfully pushed
                        dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = false }
                    }
                } else {
                    // If push failed, apply cloud preferences but keep pending flag
                    Timber.w("Pending sync failed during merge, applying cloud preferences but keeping pending flag: ${syncResult.exceptionOrNull()?.message}")
                    applyCloudPreferences(cloudPreferences)
                    // Keep pending flag to try again later
                    dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = true }
                }
            } else {
                // No pending sync, apply cloud preferences as source of truth
                applyCloudPreferences(cloudPreferences)
            }

            crashlytics.log("Completed preferences merge from Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            crashlytics.recordException(Exception("Failed to merge preferences from Firestore", e))
            Timber.e("Error merging preferences from Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Applies cloud preferences to local DataStore.
     * Used during fetch and merge process.
     */
    private suspend fun applyCloudPreferences(cloudPreferences: Map<String, Any>) {
        if (cloudPreferences.isEmpty()) {
            Timber.d("No cloud preferences to apply")
            return
        }

        dataStore.edit { preferences ->
            (cloudPreferences["darkMode"] as? Boolean)?.let { 
                preferences[PreferencesKeys.DARK_MODE] = it
            }
            (cloudPreferences["showEnglishTranslation"] as? Boolean)?.let { 
                preferences[PreferencesKeys.SHOW_ENGLISH_TRANSLATION] = it
            }
        }
        
        Timber.d("Applied cloud preferences: $cloudPreferences")
    }

    // Removed syncPendingChangesToFirestore - no longer needed with direct Firestore operations

    // Removed syncPendingPreferencesToFirestore - no longer needed with direct Firestore operations

    // Removed retryPendingSync - no longer needed with direct Firestore operations

    // Removed syncMoviesToFirestore - no longer needed with direct Firestore operations

    /**
     * Private helper to sync user preferences to Firestore.
     * Triggered after local preference changes.
     */
    private suspend fun syncPreferencesToFirestore(): Result<Unit> {
        val user = authRepository.getCurrentUser()
        if (user == null || user.isAnonymous) {
            Timber.d("Skipping preferences Firestore sync: User is anonymous or not logged in.")
            return Result.success(Unit)
        }

        try {
            val preferences = dataStore.data.first()
            val darkMode = preferences[PreferencesKeys.DARK_MODE] ?: true
            val showEnglishTranslation = preferences[PreferencesKeys.SHOW_ENGLISH_TRANSLATION] ?: false

            val result = authRepository.syncPreferencesToFirestore(
                user.uid,
                darkMode,
                showEnglishTranslation
            )

            if (result.isSuccess) {
                Timber.d("Preferences Firestore sync successful.")
                dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = false }
            } else {
                Timber.e("Preferences Firestore sync failed: ${result.exceptionOrNull()?.message}")
                dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = true }
            }
            
            return result
        } catch (e: Exception) {
            Timber.e("Error during preferences sync: ${e.message}", e)
            dataStore.edit { it[PreferencesKeys.FIRESTORE_PREFERENCES_SYNC_PENDING] = true }
            return Result.failure(e)
        }
    }
}
