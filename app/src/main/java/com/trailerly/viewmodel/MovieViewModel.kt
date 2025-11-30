package com.trailerly.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailerly.auth.AuthState
import com.trailerly.data.Firestore
import com.trailerly.data.PreferencesRepository
import com.trailerly.model.Movie
import com.trailerly.network.dto.VideoDto
import com.trailerly.repository.AuthRepository
import com.trailerly.repository.MovieRepository
import com.trailerly.uistate.UiState
import com.trailerly.uistate.dataOrNull
import com.trailerly.util.NetworkMonitor
import com.trailerly.util.TmdbConstants
import com.trailerly.util.YouTubeHelper
import com.trailerly.util.debounceMillis
import com.trailerly.MovieApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MovieViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val firestore: Firestore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private fun getRandomPage(): Int {
        return (TmdbConstants.RANDOM_PAGE_MIN..TmdbConstants.RANDOM_PAGE_MAX).random()
    }

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // Main movie list (e.g., trending or initial popular load)
    private val _movies = MutableStateFlow<UiState<List<Movie>>>(UiState.Success(emptyList())) // Initialize with empty success to show cached data instantly
    val movies: StateFlow<UiState<List<Movie>>> = _movies.asStateFlow()

    // Popular movies specific flow
    private val _popularMoviesFlow = MutableStateFlow<UiState<List<Movie>>>(UiState.Loading()) // Updated initialization
    val popularMoviesFlow: StateFlow<UiState<List<Movie>>> = _popularMoviesFlow.asStateFlow()

    // Movies categorized by genre
    private val _moviesByGenreFlow = MutableStateFlow<Map<Int, UiState<List<Movie>>>>(emptyMap())
    val moviesByGenreFlow: StateFlow<Map<Int, UiState<List<Movie>>>> = _moviesByGenreFlow.asStateFlow()

    private val _savedMovieIds = MutableStateFlow<Set<Int>>(emptySet())
    val savedMovieIds: StateFlow<Set<Int>> = _savedMovieIds.asStateFlow()

    // Real-time listener registration
    private var savedMoviesListener: com.google.firebase.firestore.ListenerRegistration? = null

    // Genre-specific StateFlows
    val actionMoviesFlow: StateFlow<UiState<List<Movie>>> = _moviesByGenreFlow.map { it[TmdbConstants.GENRE_ACTION] ?: UiState.Loading() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading()
    )

    val comedyMoviesFlow: StateFlow<UiState<List<Movie>>> = _moviesByGenreFlow.map { it[TmdbConstants.GENRE_COMEDY] ?: UiState.Loading() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading()
    )

    val romanceMoviesFlow: StateFlow<UiState<List<Movie>>> = _moviesByGenreFlow.map { it[TmdbConstants.GENRE_ROMANCE] ?: UiState.Loading() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading()
    )

    val sciFiMoviesFlow: StateFlow<UiState<List<Movie>>> = _moviesByGenreFlow.map { it[TmdbConstants.GENRE_SCIFI] ?: UiState.Loading() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading()
    )

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // API Key Error state
    private val _apiKeyError = MutableStateFlow<String?>(null)
    val apiKeyError: StateFlow<String?> = _apiKeyError.asStateFlow()

    // Refresh state for pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun clearApiKeyError() {
        _apiKeyError.value = null
    }

    // State for a single movie's details (including cast)
    private val _movieDetails = MutableStateFlow<UiState<Movie>>(UiState.Loading()) // Updated initialization
    val movieDetails: StateFlow<UiState<Movie>> = _movieDetails.asStateFlow()

    // Language toggle state
    private val _showEnglishTranslation = MutableStateFlow(false)
    val showEnglishTranslation: StateFlow<Boolean> = _showEnglishTranslation.asStateFlow()

    // Per-movie language preferences
    private val _languagePreferences = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val languagePreferences: StateFlow<Map<Int, Boolean>> = _languagePreferences.asStateFlow()

    // Trailer state management - per movie ID
    private val _trailers = MutableStateFlow<Map<Int, UiState<List<VideoDto>>>>(emptyMap())
    val trailers: StateFlow<Map<Int, UiState<List<VideoDto>>>> = _trailers.asStateFlow()

    // Cache for trailer data to avoid redundant API calls
    private val _trailersCache = MutableStateFlow<Map<Int, List<VideoDto>>>(emptyMap())

    // Search state management
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<Movie>>>(UiState.Success(emptyList()))
    val searchResults: StateFlow<UiState<List<Movie>>> = _searchResults.asStateFlow()

    // Unified cache for all known movies
    private val _allKnownMovies = MutableStateFlow<Map<Int, Movie>>(emptyMap())

    // Accumulating cache for prefetched saved movies
    private val _prefetchedSavedMovies = MutableStateFlow<Map<Int, Movie>>(emptyMap())

    private fun extractMovies(ui: UiState<List<Movie>>): List<Movie> {
        return when (ui) {
            is UiState.Success -> ui.data
            is UiState.Loading -> ui.data ?: emptyList()
            is UiState.Error -> ui.data ?: emptyList()
        }
    }

    // Exposed StateFlow for saved movies
    val savedMoviesFlow: StateFlow<List<Movie>> = combine(
        _savedMovieIds,
        _allKnownMovies
    ) { savedIds, allMoviesMap ->
        allMoviesMap.values.filter { it.id in savedIds }.toList()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun getTrailersState(movieId: Int): StateFlow<UiState<List<VideoDto>>> {
        return trailers.map { it[movieId] ?: UiState.Loading() }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            UiState.Loading()
        )
    }

    init {
        fetchInitialHomeContent()
        monitorConnectivity()

        // Load language preference
        viewModelScope.launch {
            preferencesRepository.getShowEnglishTranslation().collect { enabled ->
                _showEnglishTranslation.value = enabled
            }
        }

        // Set up debounced search
        viewModelScope.launch {
            _searchQuery
                .map { it.trim() }
                .debounceMillis(500L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _searchResults.value = UiState.Success(emptyList())
                    } else {
                        performSearch(query)
                    }
                }
        }

        // Observe all movie-related flows to update the unified cache
        viewModelScope.launch {
            combine(
                _movies,
                _popularMoviesFlow,
                _moviesByGenreFlow,
                _searchResults,
                _movieDetails,
                _prefetchedSavedMovies
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                val movies = args[0] as UiState<List<Movie>>
                @Suppress("UNCHECKED_CAST")
                val popularMovies = args[1] as UiState<List<Movie>>
                @Suppress("UNCHECKED_CAST")
                val moviesByGenre = args[2] as Map<Int, UiState<List<Movie>>>
                @Suppress("UNCHECKED_CAST")
                val searchResults = args[3] as UiState<List<Movie>>
                @Suppress("UNCHECKED_CAST")
                val movieDetails = args[4] as UiState<Movie>
                @Suppress("UNCHECKED_CAST")
                val prefetchedSavedMovies = args[5] as Map<Int, Movie>

                val updatedCache = mutableMapOf<Int, Movie>()

                // Extract from movies
                updatedCache.putAll(extractMovies(movies).associateBy { it.id })

                // Extract from popularMovies
                updatedCache.putAll(extractMovies(popularMovies).associateBy { it.id })

                // Extract from moviesByGenre
                moviesByGenre.values.forEach { uiState ->
                    updatedCache.putAll(extractMovies(uiState).associateBy { it.id })
                }

                // Extract from searchResults
                updatedCache.putAll(extractMovies(searchResults).associateBy { it.id })

                // Extract from movieDetails
                val movieDetailsList = if (movieDetails is UiState.Success) {
                    listOf(movieDetails.data)
                } else if (movieDetails is UiState.Loading && movieDetails.data != null) {
                    listOf(movieDetails.data)
                } else {
                    emptyList()
                }
                updatedCache.putAll(movieDetailsList.associateBy { it.id })

                // Extract from prefetched saved movies
                updatedCache.putAll(prefetchedSavedMovies)

                updatedCache.toMap()
            }.collectLatest { updatedCache ->
                _allKnownMovies.value = updatedCache
            }
        }

        // Observe auth state to set up real-time sync on login
        viewModelScope.launch {
            authRepository.getAuthStateFlow().collect { authState ->
                if (authState is AuthState.Authenticated && !authState.isAnonymous) {
                    Timber.d("User authenticated, setting up real-time sync.")

                    // Set up real-time listener for saved movies
                    try {
                        savedMoviesListener = firestore.listenToSavedMovies(authState.userId) { movieIds ->
                            Timber.d("Real-time update received: ${movieIds.size} saved movies")
                            _savedMovieIds.value = movieIds
                            // Fetch any missing movie details
                            fetchMissingSavedMovies()
                        }
                    } catch (e: Exception) {
                        Timber.e("Error setting up saved movies real-time listener: ${e.message}", e)
                        MovieApplication.crashlytics.recordException(Exception("Error setting up saved movies real-time listener", e))
                    }

                    // Initial load of saved movies
                    try {
                        val savedIds = firestore.getSavedMovies(authState.userId)
                        _savedMovieIds.value = savedIds
                        fetchMissingSavedMovies()
                    } catch (e: Exception) {
                        Timber.e("Error loading initial saved movies: ${e.message}", e)
                        MovieApplication.crashlytics.recordException(Exception("Error loading initial saved movies", e))
                    }
                } else {
                    // User logged out or anonymous - clear listener and saved movies
                    savedMoviesListener?.remove()
                    savedMoviesListener = null
                    _savedMovieIds.value = emptySet()
                }
            }
        }

        // Update _movies to reflect cached movies for instant display
        viewModelScope.launch {
            _allKnownMovies.collect { cachedMovies ->
                val movieList = cachedMovies.values.toList()
                val current = _movies.value.dataOrNull()
                if (movieList.isNotEmpty() && current != movieList) {
                    _movies.value = UiState.Success(movieList)
                }
            }
        }

        // Clean up listener on ViewModel destruction
        viewModelScope.launch {
            kotlinx.coroutines.awaitCancellation()
        }.invokeOnCompletion {
            savedMoviesListener?.remove()
            firestore.removeListener()
        }
    }

    private fun monitorConnectivity() {
        viewModelScope.launch {
            networkMonitor.connectivityFlow.collect { isConnected ->
                _isOffline.value = !isConnected
                if (isConnected) {
                    // Fetch any missing movie details when back online
                    val missing = _savedMovieIds.value - _allKnownMovies.value.keys
                    if (missing.isNotEmpty()) {
                        fetchMissingSavedMovies()
                    }
                }
            }
        }
    }

    // Removed loadSavedMovieIds - now handled by Firestore real-time listener

    /**
     * Fetches movie details for saved movie IDs that aren't in the cache.
     * This ensures saved movies from search results appear after app restart.
     */
    private fun fetchMissingSavedMovies() {
        viewModelScope.launch {
            try {
                val savedIds = _savedMovieIds.value
                val cachedIds = _allKnownMovies.value.keys
                val missingIds = savedIds - cachedIds

                if (missingIds.isEmpty()) return@launch

                // Skip if offline
                if (_isOffline.value) {
                    MovieApplication.crashlytics.log("Skipping fetch of ${missingIds.size} saved movies - offline")
                    return@launch
                }

                // Log for debugging
                MovieApplication.crashlytics.setCustomKey("missing_saved_movies_count", missingIds.size)
                Timber.d("Found ${missingIds.size} saved movies not in cache, fetching details")

                // Limit concurrent fetches to avoid overwhelming the API
                val semaphore = kotlinx.coroutines.sync.Semaphore(5)

                missingIds.forEach { movieId ->
                    viewModelScope.launch {
                        semaphore.acquire()
                        try {
                            val movie = repository.getMovieDetails(movieId).getOrThrow()
                            _prefetchedSavedMovies.value = _prefetchedSavedMovies.value + (movie.id to movie)
                            Timber.d("Fetched details for saved movie: $movieId")
                        } catch (e: Exception) {
                            // Handle specific error cases
                            if (e.message?.contains("404") == true || e.message?.contains("Not Found") == true) {
                                // Invalid movie ID - remove from saved movies in Firestore
                                try {
                                    val user = authRepository.getCurrentUser()
                                    if (user != null && !user.isAnonymous) {
                                        firestore.removeMovie(user.uid, movieId)
                                        _savedMovieIds.value = _savedMovieIds.value - movieId
                                        MovieApplication.crashlytics.log("Removed invalid movie ID from Firestore: $movieId")
                                    }
                                } catch (removeException: Exception) {
                                    MovieApplication.crashlytics.recordException(Exception("Failed to remove invalid movie ID from Firestore: $movieId", removeException))
                                }
                            } else {
                                // Network or other error - log but don't remove
                                Timber.w("Failed to fetch saved movie $movieId: ${e.message}")
                                MovieApplication.crashlytics.recordException(Exception("Failed to fetch saved movie details", e))
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            } catch (e: Exception) {
                MovieApplication.crashlytics.recordException(Exception("Error in fetchMissingSavedMovies", e))
            }
        }
    }

    // Function to fetch initial content for the home screen (e.g., popular movies)
    fun fetchInitialHomeContent() {
        fetchPopularMovies()
        fetchInitialGenreMovies()
    }

    private fun fetchInitialGenreMovies() {
        // Fetch for Action, Comedy, Romance, Sci-Fi
        fetchMoviesByGenre(TmdbConstants.GENRE_ACTION)
        fetchMoviesByGenre(TmdbConstants.GENRE_COMEDY)
        fetchMoviesByGenre(TmdbConstants.GENRE_ROMANCE)
        fetchMoviesByGenre(TmdbConstants.GENRE_SCIFI)
    }

    /**
     * Refreshes home content by reloading all movie categories.
     * Used for pull-to-refresh functionality.
     */
    fun refreshHomeContent() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetchInitialHomeContent()
                // Ensure minimum refresh duration for better UX
                kotlinx.coroutines.delay(500)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun fetchPopularMovies() {
        // Set _movies to loading with current cached data before API call
        _movies.value = UiState.Loading(_movies.value.dataOrNull())
        _popularMoviesFlow.value = UiState.Loading(_popularMoviesFlow.value.dataOrNull())
        viewModelScope.launch {
            if (_isOffline.value) {
                _popularMoviesFlow.value = UiState.Error(
                    message = "No internet connection",
                    isNetworkError = true,
                    data = _popularMoviesFlow.value.dataOrNull()
                )
                // Reset _movies to success with cached data on error
                _movies.value = UiState.Success(_allKnownMovies.value.values.toList())
                return@launch
            }
            try {
                val popularMovies = repository.discoverPopularMovies(page = getRandomPage()).getOrThrow()
                _popularMoviesFlow.value = UiState.Success(popularMovies)
                // Update _movies to success after fetching
                _movies.value = UiState.Success(_allKnownMovies.value.values.toList())
            } catch (e: Exception) {
                MovieApplication.crashlytics.recordException(e)
                MovieApplication.crashlytics.setCustomKey("operation", "fetch_popular_movies")
                MovieApplication.crashlytics.setCustomKey("is_offline", _isOffline.value)
                if (e.message?.contains("Invalid API key") == true) {
                    _apiKeyError.value = e.message
                }
                _popularMoviesFlow.value = UiState.Error(
                    message = e.message ?: "Failed to load popular movies",
                    isNetworkError = repository.isNetworkError(e),
                    data = _popularMoviesFlow.value.dataOrNull()
                )
                // Reset _movies to success with cached data on error
                _movies.value = UiState.Success(_allKnownMovies.value.values.toList())
            }
        }
    }

    fun fetchMoviesByGenre(genreId: Int) {
        _moviesByGenreFlow.value = _moviesByGenreFlow.value + (genreId to UiState.Loading(_moviesByGenreFlow.value[genreId]?.dataOrNull()))
        viewModelScope.launch {
            if (_isOffline.value) {
                _moviesByGenreFlow.value = _moviesByGenreFlow.value + (genreId to UiState.Error(
                    message = "No internet connection",
                    isNetworkError = true,
                    data = _moviesByGenreFlow.value[genreId]?.dataOrNull()
                ))
                return@launch
            }
        try {
            val page = getRandomPage()
            Timber.d("Fetching genre $genreId from page $page")
            val genreMovies = repository.discoverMoviesByGenre(
                genreId = genreId,
                page = page,
                sortBy = TmdbConstants.SORT_BY_RATING,
                voteCountGte = TmdbConstants.MIN_VOTE_COUNT
            ).getOrThrow()
                
                // Deduplicate movies that already exist in other categories
                val existingMovieIds = getAllDisplayedMovieIds()
                val uniqueGenreMovies = genreMovies.filter { movie -> !existingMovieIds.contains(movie.id) }
                
                _moviesByGenreFlow.value = _moviesByGenreFlow.value + (genreId to UiState.Success(uniqueGenreMovies))
            } catch (e: Exception) {
                if (e.message?.contains("Invalid API key") == true) {
                    _apiKeyError.value = e.message
                }
                _moviesByGenreFlow.value = _moviesByGenreFlow.value + (genreId to UiState.Error(
                    message = e.message ?: "Failed to load movies for genre",
                    isNetworkError = repository.isNetworkError(e),
                    data = _moviesByGenreFlow.value[genreId]?.dataOrNull()
                ))
            }
        }
    }
    
    /**
     * Gets all movie IDs currently displayed in any category on the home screen.
     * Used for deduplication across categories.
     */
    private fun getAllDisplayedMovieIds(): Set<Int> {
        val movieIds = mutableSetOf<Int>()
        
        // Add popular movies
        (_popularMoviesFlow.value as? UiState.Success)?.data?.forEach { movie ->
            movieIds.add(movie.id)
        }
        
        // Add movies from all genre categories
        _moviesByGenreFlow.value.forEach { (_, uiState) ->
            (uiState as? UiState.Success)?.data?.forEach { movie ->
                movieIds.add(movie.id)
            }
        }
        
        return movieIds
    }

    fun getMovieById(id: Int): Movie? {
        return _allKnownMovies.value[id]
    }

    /**
     * Fetches detailed movie information including cast.
     */
    fun fetchMovieDetails(movieId: Int, forceRefresh: Boolean = false) {
        // Short-circuit if not forcing refresh and we already have success for this movie
        if (!forceRefresh && _movieDetails.value is UiState.Success && (_movieDetails.value as UiState.Success).data.id == movieId) {
            return
        }
        _movieDetails.value = UiState.Loading(_movieDetails.value.dataOrNull())
        viewModelScope.launch {
            if (_isOffline.value) {
                _movieDetails.value = UiState.Error(
                    message = "No internet connection",
                    isNetworkError = true,
                    data = _movieDetails.value.dataOrNull()
                )
                return@launch
            }
            try {
                val movie = repository.getMovieDetails(movieId, includeEnglishTranslation = _showEnglishTranslation.value).getOrThrow()
                _movieDetails.value = UiState.Success(movie)
            } catch (e: Exception) {
                if (e.message?.contains("Invalid API key") == true) {
                    _apiKeyError.value = e.message
                }
                _movieDetails.value = UiState.Error(
                    message = e.message ?: "Failed to load movie details",
                    isNetworkError = repository.isNetworkError(e),
                    data = _movieDetails.value.dataOrNull()
                )
            }
        }
    }

    fun toggleSaveMovie(movieId: Int) {
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                if (user == null || user.isAnonymous) {
                    Timber.d("Cannot save movie: User not authenticated")
                    return@launch
                }

                // Get the movie data from cache
                val movie = getMovieById(movieId)
                if (movie == null) {
                    Timber.e("Cannot save movie: Movie data not found in cache")
                    return@launch
                }

                val wasSaved = movieId in _savedMovieIds.value

                // Optimistic update - update UI immediately
                val optimisticIds = if (wasSaved) {
                    _savedMovieIds.value - movieId
                } else {
                    _savedMovieIds.value + movieId
                }
                _savedMovieIds.value = optimisticIds
                Timber.d("Optimistic update: movie $movieId ${if (wasSaved) "removed" else "added"} locally")

                // Perform Firestore operation
                try {
                    if (wasSaved) {
                        firestore.removeMovie(user.uid, movieId)
                        Timber.d("Removed movie $movieId from Firestore")
                    } else {
                        firestore.addMovie(user.uid, movie)
                        Timber.d("Added movie $movieId to Firestore")
                    }
                } catch (e: Exception) {
                    Timber.e("Failed to sync movie $movieId to Firestore: ${e.message}", e)
                    // Revert optimistic update on failure
                    _savedMovieIds.value = if (wasSaved) {
                        optimisticIds + movieId
                    } else {
                        optimisticIds - movieId
                    }
                    throw e
                }
            } catch (e: Exception) {
                Timber.e("Exception during movie toggle: ${e.message}", e)
                MovieApplication.crashlytics.recordException(Exception("Failed to toggle movie save", e))
            }
        }
    }

    fun isMovieSaved(movieId: Int): Boolean {
        return movieId in _savedMovieIds.value
    }

    fun toggleLanguagePreference(movieId: Int) {
        viewModelScope.launch {
            try {
                val currentMovie = _movieDetails.value.dataOrNull()
                if (currentMovie == null) {
                    val cached = getMovieById(movieId)
                    if (cached?.originalLanguage?.lowercase() == "en") {
                        Timber.d("Language toggle skipped for English movie from cache")
                        return@launch
                    }
                }
                if (currentMovie?.originalLanguage?.lowercase() == "en") {
                    Timber.d("Language toggle skipped for English movie")
                    return@launch
                }

                val newValue = !_showEnglishTranslation.value
                preferencesRepository.setShowEnglishTranslation(newValue)
                _showEnglishTranslation.value = newValue
                // Re-fetch details with new language preference
                fetchMovieDetails(movieId, forceRefresh = true)
            } catch (e: Exception) {
                MovieApplication.crashlytics.recordException(Exception("Failed to toggle language preference", e))
            }
        }
    }

    fun getDisplayTitle(movie: Movie): String {
        if (movie.originalLanguage == "en") return movie.originalTitle ?: movie.title
        return if (_showEnglishTranslation.value && !movie.englishTitle.isNullOrBlank()) movie.englishTitle else movie.originalTitle ?: movie.title
    }

    fun getDisplayDescription(movie: Movie): String {
        if (movie.originalLanguage?.lowercase() == "en") return movie.originalDescription ?: movie.description
        return if (_showEnglishTranslation.value && !movie.englishDescription.isNullOrBlank()) movie.englishDescription!! else movie.originalDescription ?: movie.description
    }

    fun setMovieLanguagePreference(movieId: Int, showEnglish: Boolean) {
        _languagePreferences.value = _languagePreferences.value + (movieId to showEnglish)
    }

    fun getMovieLanguagePreference(movieId: Int): Boolean {
        return _languagePreferences.value[movieId] ?: _showEnglishTranslation.value
    }

    // Removed the old getMoviesByGenre() and getPopularMovies() functions.
    // Replaced by specific fetch functions and exposed StateFlows.

    fun setCurrentTab(int: Int) {
        _currentTab.value = int
    }

    fun retryFetchMovies() {
        repository.resetApiKeyValidation()
        fetchInitialHomeContent()
    }

    fun retrySearch() {
        repository.resetApiKeyValidation()
        val q = _searchQuery.value
        if (q.isNotBlank()) {
            viewModelScope.launch { performSearch(q) }
        }
    }

    /**
     * Fetches trailers for a specific movie, with caching to prevent redundant API calls.
     * @param movieId The TMDB movie ID
     */
    fun fetchTrailers(movieId: Int) {
        // Check cache first
        val cachedTrailers = _trailersCache.value[movieId]
        if (cachedTrailers != null) {
            _trailers.value = _trailers.value + (movieId to UiState.Success(cachedTrailers))
            return
        }

        // Not cached, fetch from API
        _trailers.value = _trailers.value + (movieId to UiState.Loading(_trailers.value[movieId]?.dataOrNull()))
        viewModelScope.launch {
            if (_isOffline.value) {
                _trailers.value = _trailers.value + (movieId to UiState.Error(
                    message = "No internet connection",
                    isNetworkError = true,
                    data = _trailers.value[movieId]?.dataOrNull()
                ))
                return@launch
            }
            try {
                repository.getMovieVideos(movieId).getOrThrow().let { videos ->
                    _trailersCache.value = _trailersCache.value + (movieId to videos)
                    _trailers.value = _trailers.value + (movieId to UiState.Success(videos))
                }
            } catch (e: Exception) {
                if (e.message?.contains("Invalid API key") == true) {
                    _apiKeyError.value = e.message
                }
                _trailers.value = _trailers.value + (movieId to UiState.Error(
                    message = e.message ?: "Failed to load trailers",
                    isNetworkError = repository.isNetworkError(e),
                    data = _trailers.value[movieId]?.dataOrNull()
                ))
            }
        }
    }

    /**
     * Gets the best trailer for a movie from cached data.
     * @param movieId The TMDB movie ID
     * @return The best trailer video, or null if not available
     */
    fun getTrailerForMovie(movieId: Int): VideoDto? {
        val cachedTrailers = _trailersCache.value[movieId]
        return cachedTrailers?.let { YouTubeHelper.getOfficialTrailer(it) }
    }

    /**
     * Updates the search query, triggering debounced search.
     * @param query The search query string
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clears the search query and resets search results.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = UiState.Success(emptyList())
    }

    /**
     * Clears the search query and resets search results.
     */
    fun clearSearchQuery() {
        clearSearch()
    }

    /**
     * Performs the actual search API call.
     * @param query The search query
     */
    private suspend fun performSearch(query: String) {
        _searchResults.value = UiState.Loading(_searchResults.value.dataOrNull())
        viewModelScope.launch {
            if (_isOffline.value) {
                _searchResults.value = UiState.Error(
                    message = "No internet connection",
                    isNetworkError = true,
                    data = _searchResults.value.dataOrNull()
                )
                return@launch
            }
            try {
                repository.searchMovies(query).getOrThrow().let { movies ->
                    _searchResults.value = UiState.Success(movies)
                }
            } catch (e: Exception) {
                if (e.message?.contains("Invalid API key") == true) {
                    _apiKeyError.value = e.message
                }
                _searchResults.value = UiState.Error(
                    message = e.message ?: "Search failed",
                    isNetworkError = repository.isNetworkError(e),
                    data = _searchResults.value.dataOrNull()
                )
            }
        }
    }
}
