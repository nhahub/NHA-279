package com.trailerly.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.trailerly.auth.AuthResult
import com.trailerly.auth.AuthState
import com.trailerly.util.FirebaseAuthErrorMapper
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

data class SavedMoviesCloud(val ids: Set<Int>, val lastUpdated: Long?)

/**
 * Repository class that encapsulates all Firebase Authentication operations.
 * Provides a clean API for authentication operations and state management.
 */
class AuthRepository @Inject constructor(
    private val googleSignInClient: GoogleSignInClient
) {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Real-time listeners for cross-device sync
    private var preferencesListener: ListenerRegistration? = null

    /**
     * Gets the current Firebase user if signed in.
     *
     * @return The current FirebaseUser or null if not signed in
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Returns a Flow that emits the current authentication state.
     * Listens to Firebase Auth state changes and maps them to AuthState.
     *
     * @return Flow emitting AuthState representing the current auth status
     */
    fun getAuthStateFlow(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val authState = if (user != null) {
                AuthState.Authenticated(
                    userId = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    isAnonymous = user.isAnonymous,
                    photoUrl = user.photoUrl?.toString()
                )
            } else {
                AuthState.Unauthenticated
            }
            trySend(authState)
        }

        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Signs in a user with email and password after validation.
     *
     * @param email User's email address
     * @param password User's password
     * @return AuthResult.Success on success, AuthResult.Error with message on failure
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            withTimeout(30_000L) {
                auth.signInWithEmailAndPassword(email, password).await()
            }
            AuthResult.Success
        } catch (e: FirebaseNetworkException) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        } catch (e: TimeoutCancellationException) {
            AuthResult.Error("Request timed out. Please try again")
        } catch (e: Exception) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        }
    }

    /**
     * Creates a new account with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @return AuthResult.Success on success, AuthResult.Error with message on failure
     */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return try {
            withTimeout(30_000L) {
                auth.createUserWithEmailAndPassword(email, password).await()
            }
            AuthResult.Success
        } catch (e: FirebaseNetworkException) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        } catch (e: TimeoutCancellationException) {
            AuthResult.Error("Request timed out. Please try again")
        } catch (e: Exception) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        }
    }

    /**
     * Signs in anonymously (guest mode).
     *
     * @return AuthResult.Success on success, AuthResult.Error with message on failure
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            withTimeout(30_000L) {
                auth.signInAnonymously().await()
            }
            AuthResult.Success
        } catch (e: FirebaseNetworkException) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        } catch (e: TimeoutCancellationException) {
            AuthResult.Error("Request timed out. Please try again")
        } catch (e: Exception) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        }
    }

    /**
     * Signs in with Google using an ID token.
     *
     * @param idToken The Google ID token obtained from Google Sign-In
     * @return AuthResult.Success on success, AuthResult.Error with message on failure
     */
    suspend fun signInWithGoogle(idToken: String): AuthResult {
        return try {
            withTimeout(30_000L) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
            }
            AuthResult.Success
        } catch (e: FirebaseNetworkException) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        } catch (e: TimeoutCancellationException) {
            AuthResult.Error("Request timed out. Please try again")
        } catch (e: Exception) {
            AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
        }
    }

    /**
     * Signs out the current user.
     */
    suspend fun signOut() {
        // Remove real-time listeners before signing out
        removePreferencesListener()
        auth.signOut()
        googleSignInClient.signOut().await() // Sign out from Google
    }

    /**
     * Updates the user's profile (e.g., display name).
     * @param newName The new display name for the user.
     * @return AuthResult.Success on success, AuthResult.Error with message on failure.
     */
    suspend fun updateUserProfile(newName: String): AuthResult {
        val user = auth.currentUser
        return if (user != null) {
            try {
                Timber.d("Updating user profile with display name: $newName")
                withTimeout(30_000L) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
                Timber.d("User profile updated successfully")
                AuthResult.Success
            } catch (e: FirebaseNetworkException) {
                Timber.e("Network error during profile update", e)
                AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
            } catch (e: TimeoutCancellationException) {
                Timber.e("Timeout during profile update", e)
                AuthResult.Error("Request timed out. Please try again")
            } catch (e: Exception) {
                Timber.e("Error during profile update: ${e.message}", e)
                AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
            }
        } else {
            AuthResult.Error("No authenticated user found to update profile.")
        }
    }

    /**
     * Uploads a profile picture to Firebase Storage and returns the download URL.
     *
     * @param imageUri The URI of the image to upload
     * @return Result.success(downloadUrl) on success, Result.failure(message) on failure
     */
    suspend fun uploadProfilePicture(imageUri: Uri): Result<String> {
        val user = auth.currentUser
        return if (user != null) {
            try {
                Timber.d("Uploading profile picture for user: ${user.uid}")
                withTimeout(60_000L) {
                    val storageRef = storage.reference.child("profile_pictures/${user.uid}/${System.currentTimeMillis()}.jpg")
                    storageRef.putFile(imageUri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    Timber.d("Profile picture uploaded successfully")
                    Result.success(downloadUrl.toString())
                }
            } catch (e: FirebaseNetworkException) {
                Timber.e("Network error during profile picture upload", e)
                Result.failure(Exception("No internet connection"))
            } catch (e: TimeoutCancellationException) {
                Timber.e("Timeout during profile picture upload", e)
                Result.failure(Exception("Upload timed out"))
            } catch (e: Exception) {
                Timber.e("Error during profile picture upload", e)
                Result.failure(Exception("Failed to upload profile picture. Please try again."))
            }
        } else {
            Result.failure(Exception("No authenticated user found."))
        }
    }

    /**
     * Updates the user's profile with a new photo URL.
     *
     * @param newName The new display name for the user (optional)
     * @param photoUrl The new photo URL for the user (optional)
     * @return AuthResult.Success on success, AuthResult.Error with message on failure
     */
    suspend fun updateUserProfile(newName: String, photoUrl: String? = null): AuthResult {
        val user = auth.currentUser
        return if (user != null) {
            try {
                Timber.d("Updating user profile with display name: $newName and photoUrl present: ${photoUrl != null}")
                withTimeout(30_000L) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .apply {
                            if (photoUrl != null) {
                                Timber.d("Updating profile with photo URL: $photoUrl")
                                setPhotoUri(Uri.parse(photoUrl))
                            }
                        }
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
                Timber.d("User profile updated successfully")
                AuthResult.Success
            } catch (e: FirebaseNetworkException) {
                Timber.e("Network error during profile update", e)
                AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
            } catch (e: TimeoutCancellationException) {
                Timber.e("Timeout during profile update", e)
                AuthResult.Error("Request timed out. Please try again")
            } catch (e: Exception) {
                Timber.e("Error during profile update: ${e.message}", e)
                AuthResult.Error(FirebaseAuthErrorMapper.mapFirebaseAuthError(e))
            }
        } else {
            AuthResult.Error("No authenticated user found to update profile.")
        }
    }

    // Removed sync and get saved movies methods - now handled by Firestore class

    suspend fun syncPreferencesToFirestore(
        userId: String,
        darkMode: Boolean,
        showEnglishTranslation: Boolean
    ): Result<Unit> {
        return try {
            val user = getCurrentUser()
            if (user == null || user.isAnonymous) {
                Timber.d("Skipping preferences sync: User not authenticated or anonymous")
                return Result.success(Unit)
            }

            withTimeout(30_000L) {
                val data = mapOf(
                    "darkMode" to darkMode,
                    "showEnglishTranslation" to showEnglishTranslation,
                    "lastUpdated" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .set(data, SetOptions.merge())
                    .await()
            }
            Timber.d("Successfully synced preferences to Firestore")
            Result.success(Unit)
        } catch (e: FirebaseNetworkException) {
            Timber.e("Network error while syncing preferences to Firestore: ${e.message}", e)
            Result.failure(e)
        } catch (e: TimeoutCancellationException) {
            Timber.e("Timeout while syncing preferences to Firestore: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e("Error syncing preferences to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPreferencesFromFirestore(userId: String): Result<Map<String, Any>> {
        return try {
            val user = getCurrentUser()
            if (user == null || user.isAnonymous) {
                Timber.d("Skipping preferences fetch: User not authenticated or anonymous")
                return Result.success(emptyMap())
            }

            val document = withTimeout(30_000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .get()
                    .await()
            }

            if (document.exists()) {
                val darkMode = document.getBoolean("darkMode") ?: true
                val showEnglishTranslation = document.getBoolean("showEnglishTranslation") ?: false
                val lastUpdated = document.getLong("lastUpdated") ?: System.currentTimeMillis()

                val preferences = mapOf(
                    "darkMode" to darkMode,
                    "showEnglishTranslation" to showEnglishTranslation,
                    "lastUpdated" to lastUpdated
                )

                Timber.d("Successfully fetched preferences from Firestore")
                Result.success(preferences)
            } else {
                Timber.d("No preferences document found in Firestore, emitting defaults")
                Result.success(emptyMap())
            }
        } catch (e: FirebaseNetworkException) {
            Timber.e("Network error while fetching preferences from Firestore: ${e.message}", e)
            Result.failure(e)
        } catch (e: TimeoutCancellationException) {
            Timber.e("Timeout while fetching preferences from Firestore: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e("Error fetching preferences from Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Removed getSavedMoviesRealtimeFlow - no longer needed

    /**
     * Sets up a real-time listener for user preferences changes in Firestore.
     * Emits updates whenever preferences change on any device.
     *
     * @param userId The authenticated user's ID
     * @return Flow emitting preference updates from Firestore
     */
    fun getPreferencesRealtimeFlow(userId: String): Flow<Map<String, Any>> = callbackFlow {
        val user = getCurrentUser()
        if (user == null || user.isAnonymous) {
            Timber.d("Skipping real-time preferences listener: User not authenticated or anonymous")
            awaitClose()
            return@callbackFlow
        }

        // Remove any existing listener
        removePreferencesListener()

        val listener = firestore.collection("users")
            .document(userId)
            .collection("preferences")
            .document("settings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e("Error in preferences real-time listener: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val darkMode = snapshot.getBoolean("darkMode") ?: true
                    val showEnglishTranslation = snapshot.getBoolean("showEnglishTranslation") ?: false
                    val lastUpdated = snapshot.getLong("lastUpdated") ?: System.currentTimeMillis()

                    val preferences = mapOf(
                        "darkMode" to darkMode,
                        "showEnglishTranslation" to showEnglishTranslation,
                        "lastUpdated" to lastUpdated
                    )

                    Timber.d("Real-time update: preferences from Firestore")
                    trySend(preferences)
                } else {
                    Timber.d("No preferences document in Firestore, emitting defaults")
                    trySend(emptyMap())
                }
            }

        preferencesListener = listener
        Timber.d("Started real-time listener for preferences")

        awaitClose {
            removePreferencesListener()
        }
    }

    // Removed removeSavedMoviesListener - no longer needed

    /**
     * Removes the preferences real-time listener.
     */
    private fun removePreferencesListener() {
        preferencesListener?.remove()
        preferencesListener = null
        Timber.d("Removed preferences real-time listener")
    }
}
