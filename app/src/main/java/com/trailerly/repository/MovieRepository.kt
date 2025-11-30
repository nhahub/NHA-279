package com.trailerly.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trailerly.model.Movie
import com.trailerly.network.ApiClient
import com.trailerly.network.dto.VideoDto
import com.trailerly.network.mapper.MovieMapper.toDomainModel
import com.trailerly.util.TmdbConstants
import com.trailerly.util.YouTubeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.trailerly.network.YouTubeApiClient
import com.trailerly.network.dto.MovieListResponse
import com.trailerly.network.dto.CreditsResponse
import com.trailerly.network.dto.VideosResponse
import com.trailerly.network.dto.MovieDetailsDto
import kotlin.Result

class MovieRepository {
    private val apiService = ApiClient.tmdbApiService
    private val crashlytics = FirebaseCrashlytics.getInstance()
    @Volatile private var isApiKeyValidated = false
    @Volatile private var isApiKeyValid = false
    private val validationMutex = Mutex()
    private val lock = Any()

    suspend fun getMovieDetails(movieId: Int, includeEnglishTranslation: Boolean = false): Result<Movie> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Fetching movie details for ID: %d, includeEnglishTranslation: %b", movieId, includeEnglishTranslation)

                // Step 1: Fetch movie details with preferred language to get original_language
                val defaultDetails = apiService.getMovieDetails(movieId, language = TmdbConstants.getPreferredLanguage())
                Timber.d("Fetched default movie details for ID: %d", movieId)

                val originalLanguage = defaultDetails.originalLanguage ?: "en"
                var finalDetails = defaultDetails
                var englishDetails: MovieDetailsDto? = null

                // Step 2: If original language differs from en-US, fetch with original language
                if (originalLanguage != "en") {
                    try {
                        val originalDetails = apiService.getMovieDetails(movieId, language = originalLanguage)
                        finalDetails = originalDetails
                        Timber.d("Fetched original language details for ID: %d in language: %s", movieId, originalLanguage)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch original language details, using default")
                        crashlytics.recordException(e)
                    }
                }

                // Step 3: If includeEnglishTranslation is true and original language is not en, fetch English
                if (includeEnglishTranslation && originalLanguage != "en") {
                    try {
                        englishDetails = apiService.getMovieDetails(movieId, language = "en-US")
                        Timber.d("Fetched English translation for ID: %d", movieId)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch English translation, proceeding without it")
                        crashlytics.recordException(e)
                    }
                }

                // Step 4: Fetch credits
                val credits = apiService.getMovieCredits(movieId)
                Timber.d("Fetched credits for movie ID: %d", movieId)

                // Step 5: Map to domain model
                val movie = if (englishDetails != null) {
                    finalDetails.toDomainModel(credits, englishDetails)
                } else {
                    finalDetails.toDomainModel(credits)
                }

                Timber.d("Successfully mapped movie details for ID: %d", movieId)
                Result.success(movie)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while fetching movie details for ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieDetails")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while fetching movie details for ID: %d, code: %d", movieId, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieDetails")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while fetching movie details for ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieDetails")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while fetching movie details for ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieDetails")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while fetching movie details for ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieDetails")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        }
    }

    suspend fun discoverPopularMovies(page: Int = 1): Result<List<Movie>> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Discovering popular movies (page: %d, sortBy: %s, voteCountGte: %d)",
                    page, TmdbConstants.SORT_BY_RATING, TmdbConstants.MIN_VOTE_COUNT)

                val response = apiService.discoverMovies(
                    language = TmdbConstants.getPreferredLanguage(),
                    page = page,
                    sortBy = TmdbConstants.SORT_BY_RATING,
                    voteCountGte = TmdbConstants.MIN_VOTE_COUNT
                )

                val movies = response.results.map { it.toDomainModel() }
                Timber.d("Successfully discovered %d popular movies on page %d", movies.size, page)
                Result.success(movies)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while discovering popular movies on page %d", page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverPopularMovies")
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while discovering popular movies on page %d, code: %d", page, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverPopularMovies")
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while discovering popular movies on page %d", page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverPopularMovies")
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while discovering popular movies on page %d", page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverPopularMovies")
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while discovering popular movies on page %d", page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverPopularMovies")
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        }
    }

    suspend fun discoverMoviesByGenre(
        genreId: Int,
        page: Int = 1,
        sortBy: String = TmdbConstants.SORT_BY_RATING,
        voteCountGte: Int = TmdbConstants.MIN_VOTE_COUNT
    ): Result<List<Movie>> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Discovering movies by genre (genreId: %d, page: %d, sortBy: %s, voteCountGte: %d)",
                    genreId, page, sortBy, voteCountGte)

                val response = apiService.discoverMovies(
                    language = TmdbConstants.getPreferredLanguage(),
                    page = page,
                    genreIds = genreId.toString(),
                    sortBy = sortBy,
                    voteCountGte = voteCountGte
                )

                val movies = response.results.map { it.toDomainModel() }
                Timber.d("Successfully discovered %d movies for genre %d on page %d", movies.size, genreId, page)
                Result.success(movies)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while discovering movies by genre %d on page %d", genreId, page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverMoviesByGenre")
            crashlytics.setCustomKey("genre_id", genreId)
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while discovering movies by genre %d on page %d, code: %d", genreId, page, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverMoviesByGenre")
            crashlytics.setCustomKey("genre_id", genreId)
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while discovering movies by genre %d on page %d", genreId, page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverMoviesByGenre")
            crashlytics.setCustomKey("genre_id", genreId)
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while discovering movies by genre %d on page %d", genreId, page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverMoviesByGenre")
            crashlytics.setCustomKey("genre_id", genreId)
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while discovering movies by genre %d on page %d", genreId, page)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "discoverMoviesByGenre")
            crashlytics.setCustomKey("genre_id", genreId)
            crashlytics.setCustomKey("page", page)
            Result.failure(e)
        }
    }

    suspend fun getMovieVideos(movieId: Int): Result<List<VideoDto>> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Fetching videos for movie ID: %d", movieId)

                // Primary: Fetch from TMDb
                val videosResponse = apiService.getMovieVideos(movieId, language = TmdbConstants.getPreferredLanguage())
                var videos = videosResponse.results
                Timber.d("Fetched %d videos from TMDb for movie ID: %d", videos.size, movieId)

                // Check if we have a suitable trailer
                val hasOfficialTrailer = YouTubeHelper.getOfficialTrailer(videos) != null
                Timber.d("TMDb has official trailer: %b", hasOfficialTrailer)

                // Fallback to YouTube if no TMDb trailer
                if (!hasOfficialTrailer) {
                    try {
                        Timber.d("No TMDb trailer found, attempting YouTube fallback for movie ID: %d", movieId)

                        // Get movie title and year for YouTube search
                        val movieDetails = apiService.getMovieDetails(movieId)
                        val movieTitle = movieDetails.originalTitle ?: movieDetails.title ?: ""
                        val movieYear = movieDetails.releaseDate?.substring(0, 4)?.toIntOrNull() // Extract year from release date
                        Timber.d("Retrieved movie title '%s' and year %d for YouTube search", movieTitle, movieYear)

                        if (movieTitle.isNotBlank()) {
                            val youtubeTrailer = YouTubeHelper.searchYouTubeTrailer(movieTitle, movieYear)
                            if (youtubeTrailer != null) {
                                videos = videos + listOf(youtubeTrailer)
                                Timber.d("Successfully added YouTube trailer fallback for movie ID: %d", movieId)
                            } else {
                                Timber.d("YouTube search returned no results for movie ID: %d", movieId)
                            }
                        } else {
                            Timber.w("Movie title is blank, skipping YouTube fallback for movie ID: %d", movieId)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "YouTube fallback failed for movie ID: %d", movieId)
                        crashlytics.recordException(e)
                        // Continue with TMDb results only
                    }
                }

                Timber.d("Returning %d total videos for movie ID: %d", videos.size, movieId)
                Result.success(videos)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while fetching videos for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieVideos")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while fetching videos for movie ID: %d, code: %d", movieId, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieVideos")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while fetching videos for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieVideos")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while fetching videos for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieVideos")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while fetching videos for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieVideos")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        }
    }

    suspend fun searchMovies(query: String): Result<List<Movie>> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Searching movies with query: '%s'", query)

                val response = apiService.searchMovies(query = query, language = TmdbConstants.getPreferredLanguage())
                val movies = response.results.map { it.toDomainModel() }

                Timber.d("Successfully found %d movies for query: '%s'", movies.size, query)
                Result.success(movies)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while searching movies with query: '%s'", query)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "searchMovies")
            crashlytics.setCustomKey("query", query)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while searching movies with query: '%s', code: %d", query, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "searchMovies")
            crashlytics.setCustomKey("query", query)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while searching movies with query: '%s'", query)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "searchMovies")
            crashlytics.setCustomKey("query", query)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while searching movies with query: '%s'", query)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "searchMovies")
            crashlytics.setCustomKey("query", query)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while searching movies with query: '%s'", query)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "searchMovies")
            crashlytics.setCustomKey("query", query)
            Result.failure(e)
        }
    }

    suspend fun getMovieRecommendations(movieId: Int): Result<List<Movie>> {
        return try {
            withTimeout(30_000L) {
                Timber.d("Fetching recommendations for movie ID: %d", movieId)

                val response = apiService.getMovieRecommendations(movieId, language = TmdbConstants.getPreferredLanguage())
                val movies = response.results.map { it.toDomainModel() }

                Timber.d("Successfully fetched %d recommendations for movie ID: %d", movies.size, movieId)
                Result.success(movies)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Request timed out while fetching recommendations for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieRecommendations")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error while fetching recommendations for movie ID: %d, code: %d", movieId, e.code())
            if (e.code() == 401) {
                validationMutex.withLock {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                }
            }
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieRecommendations")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: IOException) {
            Timber.e(e, "Network error while fetching recommendations for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieRecommendations")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: JsonDataException) {
            Timber.e(e, "JSON parsing error while fetching recommendations for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieRecommendations")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while fetching recommendations for movie ID: %d", movieId)
            crashlytics.recordException(e)
            crashlytics.setCustomKey("operation", "getMovieRecommendations")
            crashlytics.setCustomKey("movie_id", movieId)
            Result.failure(e)
        }
    }

    fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is IOException ||
               throwable is HttpException ||
               throwable is TimeoutCancellationException
    }

    fun resetApiKeyValidation() {
        synchronized(lock) {
            isApiKeyValidated = false
            isApiKeyValid = false
            Timber.d("API key validation reset")
        }
    }

    private suspend fun validateApiKey(): Boolean {
        return validationMutex.withLock {
            if (isApiKeyValidated) {
                return@withLock isApiKeyValid
            }

            try {
                Timber.d("Validating API key")
                val response = apiService.getConfiguration()
                if (response.isSuccessful()) {
                    isApiKeyValidated = true
                    isApiKeyValid = true
                    Timber.d("API key validation successful")
                    true
                } else if (response.code() == 401) {
                    isApiKeyValidated = true
                    isApiKeyValid = false
                    Timber.e("API key validation failed: Invalid API key")
                    crashlytics.recordException(Exception("Invalid API key"))
                    crashlytics.setCustomKey("operation", "validateApiKey")
                    false
                } else {
                    Timber.e("API key validation failed with code: ${response.code()}")
                    crashlytics.recordException(Exception("API validation failed with code ${response.code()}"))
                    crashlytics.setCustomKey("operation", "validateApiKey")
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "API key validation failed with unexpected error")
                crashlytics.recordException(e)
                crashlytics.setCustomKey("operation", "validateApiKey")
                false
            }
        }
    }
}
