package com.trailerly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.* // Added for collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trailerly.auth.AuthState
import com.trailerly.ui.components.BottomNavigationBar
import com.trailerly.ui.components.GuestModeDialog
import com.trailerly.ui.components.ImageWithFallback
import com.trailerly.ui.components.OfflineBanner
import com.trailerly.viewmodel.AuthViewModel
import com.trailerly.viewmodel.MovieViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMoviesScreen(
    viewModel: MovieViewModel,
    onMovieClick: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToProfile: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    onNavigateToSignIn: () -> Unit = {}
) {
    val savedMovies by viewModel.savedMoviesFlow.collectAsState() // Use savedMoviesFlow
    val isOffline by viewModel.isOffline.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val isGuest = authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Movies") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = 1,
                onTabSelected = { tab ->
                    viewModel.setCurrentTab(tab)
                    when (tab) {
                        0 -> onNavigateToHome()
                        1 -> { /* Already on saved */ }
                        2 -> onNavigateToProfile()
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(isOffline)

            if (savedMovies.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isGuest) "Sign in to save movies" else "No saved movies yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isGuest)
                            ""
                        else
                            "Save your favorite movies to watch them later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (isGuest) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = { onNavigateToSignIn() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign In")
                        }
                    }
                }
            }
        } else {
            // Grid of Saved Movies
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(savedMovies, key = { it.id }) { movie -> // Added key for efficient recomposition
                    Box {
                        Card(
                            onClick = { onMovieClick(movie.id) },
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.height(256.dp)) {
                                ImageWithFallback(
                                    imageUrl = movie.posterUrl,
                                    contentDescription = movie.originalTitle ?: movie.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.8f)
                                                    )
                                                )
                                            )
                                    )

                                    Text(
                                        text = movie.originalTitle ?: movie.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp)
                                    )
                                }
                            }
                        }

                        // Remove Button
                        IconButton(
                            onClick = { viewModel.toggleSaveMovie(movie.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.8f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkRemove,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp)
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
