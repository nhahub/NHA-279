package com.trailerly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import com.trailerly.R
import com.trailerly.model.Movie
import com.trailerly.uistate.UiState
import com.trailerly.viewmodel.MovieViewModel
import com.trailerly.ui.components.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: MovieViewModel = hiltViewModel(),
    onMovieClick: (Int) -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val popularMoviesState by viewModel.popularMoviesFlow.collectAsStateWithLifecycle()
    val actionMoviesState by viewModel.actionMoviesFlow.collectAsStateWithLifecycle()
    val comedyMoviesState by viewModel.comedyMoviesFlow.collectAsStateWithLifecycle()
    val romanceMoviesState by viewModel.romanceMoviesFlow.collectAsStateWithLifecycle()
    val sciFiMoviesState by viewModel.sciFiMoviesFlow.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val apiKeyError by viewModel.apiKeyError.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val pullRefreshState = rememberPullRefreshState(isRefreshing, { viewModel.refreshHomeContent() })

    val refreshEnabled = searchQuery.isBlank()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = viewModel::clearSearchQuery) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = 0,
                onTabSelected = { tab ->
                    when (tab) {
                        0 -> {} // Already on Home
                        1 -> onNavigateToSaved()
                        2 -> onNavigateToProfile()
                    }
                    viewModel.setCurrentTab(tab)
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OfflineBanner(isOffline)

            if (!refreshEnabled) {
                val currentSearchResults = searchResults
                when (currentSearchResults) {
                    is UiState.Loading -> LoadingStateView()
                    is UiState.Error -> ErrorStateView(
                        message = currentSearchResults.message ?: "Search failed",
                        onRetry = viewModel::retrySearch
                    )
                    is UiState.Success -> {
                        if ((currentSearchResults as UiState.Success).data.isNullOrEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No results found",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items((currentSearchResults as UiState.Success).data ?: emptyList()) { movie ->
                                    MovieCard(
                                        title = movie.originalTitle ?: movie.title,
                                        posterUrl = movie.posterUrl,
                                        rating = movie.rating,
                                        onClick = { onMovieClick(movie.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    MovieCarousels(
                        popularMoviesState = popularMoviesState,
                        actionMoviesState = actionMoviesState,
                        comedyMoviesState = comedyMoviesState,
                        romanceMoviesState = romanceMoviesState,
                        sciFiMoviesState = sciFiMoviesState,
                        onMovieClick = onMovieClick,
                        viewModel = viewModel
                    )
                    
                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    apiKeyError?.let { error ->
        ApiKeyErrorDialog(
            errorMessage = error,
            onDismiss = { viewModel.clearApiKeyError() },
            onRetry = { viewModel.retryFetchMovies() }
        )
    }

    // GuestModeDialog if needed
}

@Composable
fun MovieCarousels(
    popularMoviesState: UiState<List<Movie>>,
    actionMoviesState: UiState<List<Movie>>,
    comedyMoviesState: UiState<List<Movie>>,
    romanceMoviesState: UiState<List<Movie>>,
    sciFiMoviesState: UiState<List<Movie>>,
    onMovieClick: (Int) -> Unit,
    viewModel: MovieViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Popular",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val currentPopularMoviesState = popularMoviesState
            when (currentPopularMoviesState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(
                    message = currentPopularMoviesState.message ?: "Failed to load popular movies",
                    onRetry = viewModel::retryFetchMovies
                )
                is UiState.Success -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentPopularMoviesState.data ?: emptyList()) { movie ->
                        MovieCard(
                            title = movie.originalTitle ?: movie.title,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Action",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val currentActionMoviesState = actionMoviesState
            when (currentActionMoviesState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(
                    message = currentActionMoviesState.message ?: "Failed to load action movies",
                    onRetry = viewModel::retryFetchMovies
                )
                is UiState.Success -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentActionMoviesState.data ?: emptyList()) { movie ->
                        MovieCard(
                            title = movie.originalTitle ?: movie.title,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Comedy",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val currentComedyMoviesState = comedyMoviesState
            when (currentComedyMoviesState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(
                    message = currentComedyMoviesState.message ?: "Failed to load comedy movies",
                    onRetry = viewModel::retryFetchMovies
                )
                is UiState.Success -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentComedyMoviesState.data ?: emptyList()) { movie ->
                        MovieCard(
                            title = movie.originalTitle ?: movie.title,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Romance",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val currentRomanceMoviesState = romanceMoviesState
            when (currentRomanceMoviesState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(
                    message = currentRomanceMoviesState.message ?: "Failed to load romance movies",
                    onRetry = viewModel::retryFetchMovies
                )
                is UiState.Success -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentRomanceMoviesState.data ?: emptyList()) { movie ->
                        MovieCard(
                            title = movie.originalTitle ?: movie.title,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Sci-Fi",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val currentSciFiMoviesState = sciFiMoviesState
            when (currentSciFiMoviesState) {
                is UiState.Loading -> LoadingStateView()
                is UiState.Error -> ErrorStateView(
                    message = currentSciFiMoviesState.message ?: "Failed to load sci-fi movies",
                    onRetry = viewModel::retryFetchMovies
                )
                is UiState.Success -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentSciFiMoviesState.data ?: emptyList()) { movie ->
                        MovieCard(
                            title = movie.originalTitle ?: movie.title,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }
    }
}