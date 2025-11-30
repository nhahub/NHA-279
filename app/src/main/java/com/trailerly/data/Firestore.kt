package com.trailerly.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.trailerly.model.Movie
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Clean Firestore class for movie save and sync operations.
 * Firestore is the only data source - no local storage or cache.
 */
class Firestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Real-time listener registration
    private var savedMoviesListener: ListenerRegistration? = null

    /**
     * Safely converts various number types to Int.
     */
    private fun convertToInt(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    /**
     * Removes the real-time listener.
     */
    fun removeListener() {
        savedMoviesListener?.remove()
        savedMoviesListener = null
        Timber.d("Removed saved movies real-time listener")
    }

    /**
     * Adds a movie to the user's saved list.
     */
    suspend fun addMovie(userId: String, movieData: Movie) {
        try {
            withTimeout(30_000L) {
                // Get current saved movies
                val currentIds = getSavedMovies(userId).toSet()

                // Add new movie ID
                val updatedIds = currentIds + movieData.id

                // Update Firestore
                val data = mapOf(
                    "movieIds" to updatedIds.toList(),
                    "lastUpdated" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_movies")
                    .document("list")
                    .set(data, SetOptions.merge())
                    .await()

                Timber.d("Successfully added movie ${movieData.id} for user $userId")
            }
        } catch (e: Exception) {
            Timber.e("Failed to add movie ${movieData.id}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Removes a movie from the user's saved list.
     */
    suspend fun removeMovie(userId: String, movieId: Int) {
        try {
            withTimeout(30_000L) {
                // Get current saved movies
                val currentIds = getSavedMovies(userId).toSet()

                // Remove movie ID
                val updatedIds = currentIds - movieId

                // Update Firestore
                val data = mapOf(
                    "movieIds" to updatedIds.toList(),
                    "lastUpdated" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_movies")
                    .document("list")
                    .set(data, SetOptions.merge())
                    .await()

                Timber.d("Successfully removed movie $movieId for user $userId")
            }
        } catch (e: Exception) {
            Timber.e("Failed to remove movie $movieId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets the current saved movie IDs for a user.
     */
    suspend fun getSavedMovies(userId: String): Set<Int> {
        return try {
            withTimeout(30_000L) {
                val document = firestore.collection("users")
                    .document(userId)
                    .collection("saved_movies")
                    .document("list")
                    .get()
                    .await()

                if (document.exists()) {
                    val rawMovieIds = document.get("movieIds") as? List<*> ?: emptyList<Any>()
                    rawMovieIds.mapNotNull { convertToInt(it) }.toSet()
                } else {
                    emptySet()
                }
            }
        } catch (e: Exception) {
            Timber.e("Failed to get saved movies: ${e.message}", e)
            emptySet()
        }
    }

    /**
     * Listens to real-time changes in saved movies.
     * Emits the current set of saved movie IDs whenever they change.
     */
    fun listenToSavedMovies(userId: String, callback: (Set<Int>) -> Unit): ListenerRegistration {
        // Remove any existing listener
        removeListener()

        val listener = firestore.collection("users")
            .document(userId)
            .collection("saved_movies")
            .document("list")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e("Real-time listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val rawMovieIds = snapshot.get("movieIds") as? List<*> ?: emptyList<Any>()
                    val movieIds = rawMovieIds.mapNotNull { convertToInt(it) }.toSet()
                    Timber.d("Real-time update: ${movieIds.size} saved movies")
                    callback(movieIds)
                } else {
                    Timber.d("No saved movies document found")
                    callback(emptySet())
                }
            }

        savedMoviesListener = listener
        Timber.d("Started real-time listener for user: $userId")
        return listener
    }

    /**
     * Alternative Flow-based method for listening to saved movies changes.
     */
    fun listenToSavedMovies(userId: String): Flow<Set<Int>> = callbackFlow {
        val listener = listenToSavedMovies(userId) { movieIds ->
            trySend(movieIds)
        }

        awaitClose {
            listener.remove()
        }
    }
}
