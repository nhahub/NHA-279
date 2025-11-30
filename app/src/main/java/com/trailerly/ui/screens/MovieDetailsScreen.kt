package com.trailerly.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.trailerly.R
import com.trailerly.auth.AuthState
import com.trailerly.network.dto.VideoDto
import com.trailerly.ui.components.ApiKeyErrorDialog
import com.trailerly.ui.components.ErrorStateView
import com.trailerly.ui.components.GuestModeDialog
import com.trailerly.ui.components.ImageWithFallback
import com.trailerly.ui.components.LoadingStateView
import com.trailerly.ui.components.OfflineBanner
import com.trailerly.ui.components.YouTubePlayerComposable
import com.trailerly.uistate.UiState
import com.trailerly.util.YouTubeHelper
import com.trailerly.viewmodel.AuthViewModel
import com.trailerly.viewmodel.MovieViewModel
import com.trailerly.model.Movie
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.max
import kotlin.math.min

fun formatCharacter(character: String?): String {
    if (character.isNullOrBlank()) return ""
    val beforeSlash = character.split("/").firstOrNull()?.trim() ?: ""
    return beforeSlash
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movieId: Int,
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    isGuest: Boolean = false,
    onNavigateToSignIn: () -> Unit = {}
) {
    val movieDetailsState by viewModel.movieDetails.collectAsState()
    val savedMovieIds by viewModel.savedMovieIds.collectAsState()
    val isSaved = savedMovieIds.contains(movieId)
    val showEnglishTranslation by viewModel.showEnglishTranslation.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isOffline by authViewModel.isOffline.collectAsState()
    val apiKeyError by viewModel.apiKeyError.collectAsState()

    // Trailer state - per movie
    val trailersState by viewModel.getTrailersState(movieId).collectAsState()
    val trailer = (trailersState as? UiState.Success)?.data?.let { YouTubeHelper.getOfficialTrailer(it) }
    val safeVideoKey = trailer?.key?.takeIf { it.isNotBlank() }
    var showInlineTrailer by remember { mutableStateOf(false) } // State to control inline trailer visibility

    // Calculate responsive hero image height
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val heroImageHeight = max(280f, min(450f, screenHeightDp.value * 0.5f)).dp

    // Fetch movie details and trailers when screen loads
    LaunchedEffect(movieId) {
        viewModel.fetchMovieDetails(movieId)
        viewModel.fetchTrailers(movieId)
    }

    // API Key Error Dialog
    apiKeyError?.let { errorMessage ->
        ApiKeyErrorDialog(
            errorMessage = errorMessage,
            onRetry = {
                viewModel.clearApiKeyError()
                viewModel.fetchMovieDetails(movieId)
                viewModel.fetchTrailers(movieId)
            },
            onDismiss = viewModel::clearApiKeyError
        )
    }

    when (movieDetailsState) {
        is UiState.Success -> {
            val movie = (movieDetailsState as UiState.Success).data
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.movie_details_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    item {
                        OfflineBanner(isOffline)
                    }
                    // Hero Image
                    item {
                        val density = LocalDensity.current
                        val startY = with(density) { (heroImageHeight * 0.5f).toPx() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroImageHeight)
                        ) {
                            ImageWithFallback(
                                imageUrl = movie.backdropUrl ?: movie.posterUrl,
                                contentDescription = viewModel.getDisplayTitle(movie),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Black
                                            ),
                                            startY = startY
                                        )
                                    )
                            )
                        }
                    }

                                    // Inline Trailer Section
                                    if (showInlineTrailer && safeVideoKey != null && safeVideoKey != "youtube_search") {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp)
                                            ) {
                                                YouTubePlayerComposable(
                                                    videoId = safeVideoKey,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(16f / 9f) // Standard YouTube aspect ratio
                                                )
                                            }
                                        }
                                    }

                    // Movie Info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Poster and Details Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Smaller Poster
                                ImageWithFallback(
                                    imageUrl = movie.posterUrl,
                                    contentDescription = viewModel.getDisplayTitle(movie),
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                // Details Column
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Movie Name
                                    Text(
                                        text = viewModel.getDisplayTitle(movie),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )


                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = Color.Yellow,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "%.1f".format(movie.rating),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (movie.releaseYear != null) {
                                            Text(
                                                text = movie.releaseYear!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilterChip(
                                            selected = true,
                                            onClick = { },
                                            label = { Text(movie.genre) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                // Language Toggle Button
                                if (movie.originalLanguage?.lowercase() != "en") {
                                    IconButton(
                                        onClick = { viewModel.toggleLanguagePreference(movieId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Language,
                                            contentDescription = "Toggle language",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                var showGuestDialog by remember { mutableStateOf(false) }

                                if (showGuestDialog) {
                                    GuestModeDialog(
                                        onSignInClick = {
                                            showGuestDialog = false
                                            onNavigateToSignIn()
                                        },
                                        onDismiss = { showGuestDialog = false }
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isGuest) {
                                            showGuestDialog = true
                                        } else {
                                            viewModel.toggleSaveMovie(movieId)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = if (isSaved) "Removed from saved movies" else "Added to saved movies"
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSaved)
                                            MaterialTheme.colorScheme.surfaceVariant
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isSaved) "Saved" else "Save")
                                }

                                when (trailersState) {
                                    is UiState.Loading -> {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    is UiState.Success -> {
                                        if (safeVideoKey != null && safeVideoKey != "youtube_search") {
                                            OutlinedButton(
                                                onClick = {
                                                    showInlineTrailer = !showInlineTrailer
                                                }, // Toggle inline trailer visibility
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                enabled = !isOffline
                                            ) {
                                                Icon(
                                                    imageVector = if (showInlineTrailer) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Trailer")
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val trailerUrl = YouTubeHelper.buildYouTubeUrl(safeVideoKey)
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "Check out the trailer for ${viewModel.getDisplayTitle(movie)}: $trailerUrl"
                                                        )
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Trailer"))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                enabled = !isOffline
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Share,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Share")
                                            }
                                        } else {
                                            // No trailer available, display a disabled button or message
                                            OutlinedCard(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline
                                                )
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.VideoLibrary,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.38f
                                                            )
                                                        )
                                                        Text(
                                                            text = "Trailer unavailable",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.38f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is UiState.Error -> {
                                        (trailersState as UiState.Error).let { errorState ->
                                            Text(
                                                text = errorState.message,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                    }

                                    else -> {
                                        // Handle any other states if UiState has more subclasses
                                        Text(
                                            text = "Unknown state",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Description
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.description),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = viewModel.getDisplayDescription(movie),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Cast
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "Main Cast",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (movie.actors.isEmpty()) {
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.3f
                                            )
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.6f
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Cast details unavailable",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(movie.actors) { actor ->
                                            Column(
                                                modifier = Modifier.width(96.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ImageWithFallback(
                                                    imageUrl = actor.profileImageUrl,
                                                    contentDescription = actor.name,
                                                    modifier = Modifier
                                                        .size(96.dp)
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop,
                                                    fallbackIcon = Icons.Filled.Person
                                                )

                                                Text(
                                                    text = actor.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    softWrap = true,
                                                    textAlign = TextAlign.Center
                                                )
                                                val formattedCharacter =
                                                    formatCharacter(actor.character)
                                                if (formattedCharacter.isNotBlank()) {
                                                    Text(
                                                        text = formattedCharacter,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        softWrap = true,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is UiState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.movie_details_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorStateView(
                        message = (movieDetailsState as UiState.Error<Movie>).message,
                        onRetry = { viewModel.fetchMovieDetails(movieId) }
                    )
                }
            }
        }

        is UiState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.movie_details_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingStateView()
                }
            }
        }
    }
}
