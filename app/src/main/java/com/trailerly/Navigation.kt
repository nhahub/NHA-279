package com.trailerly

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trailerly.auth.AuthResult
import com.trailerly.auth.AuthState
import com.trailerly.ui.components.LoadingStateView
import com.trailerly.ui.screens.HomeScreen
import com.trailerly.ui.screens.MovieDetailsScreen
import com.trailerly.ui.screens.SavedMoviesScreen
import com.trailerly.ui.screens.ProfileScreen
import com.trailerly.ui.screens.EditProfileScreen
import com.trailerly.ui.screens.SignInScreen
import com.trailerly.viewmodel.AuthViewModel
import com.trailerly.viewmodel.MovieViewModel
import com.trailerly.viewmodel.ProfileViewModel
import timber.log.Timber

@Composable
fun TrailerlyNavigation(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val movieViewModel: MovieViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    val authState by authViewModel.authState.collectAsState()

    // Create a launcher for Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Timber.d("Google Sign-In successful")
            } else {
                Timber.e("Google Sign-In failed with result code: ${result.resultCode}")
            }
        }
    )

    // Dynamic start destination based on auth state
    val startDestination = when (authState) {
        is AuthState.Authenticated -> "home"
        is AuthState.Unauthenticated -> "signin"
        is AuthState.Loading -> "loading"
        is AuthState.Error -> "signin"
        else -> "signin"
    }

    val isGuest = authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous

    // Reactive navigation based on auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                val isAnonymous = (authState as AuthState.Authenticated).isAnonymous
                // Only navigate to home if not anonymous (guest users can navigate freely)
                if (!isAnonymous && navController.currentDestination?.route != "home") {
                    navController.navigate("home") {
                        popUpTo("signin") { inclusive = true }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                if (navController.currentDestination?.route != "signin") {
                    navController.navigate("signin") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300))
        }
    ) {
        composable("loading") {
            LoadingStateView()
        }
        composable("signin") {
            SignInScreen(
                viewModel = authViewModel,
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("signin") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = movieViewModel,
                onMovieClick = { movieId ->
                    navController.navigate("movie/$movieId")
                },
                onNavigateToSaved = {
                    navController.navigate("saved")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToSignIn = {
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "movie/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.IntType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
            MovieDetailsScreen(
                movieId = movieId,
                viewModel = movieViewModel,
                onBackClick = { navController.popBackStack() },
                isGuest = isGuest,
                onNavigateToSignIn = {
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable("saved") {
            SavedMoviesScreen(
                viewModel = movieViewModel,
                onMovieClick = { movieId ->
                    navController.navigate("movie/$movieId")
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToSignIn = {
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable("profile") {
            ProfileScreen(
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToSaved = {
                    navController.navigate("saved")
                },
                onNavigateToEditProfile = { navController.navigate("edit_profile") }, // Added navigation to EditProfileScreen
                viewModel = profileViewModel,
                onNavigateToSignIn = {
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }

        composable("edit_profile") { // New route for EditProfileScreen
            Timber.d("Navigating to edit_profile route - AuthState: ${authState::class.simpleName}")
            FirebaseCrashlytics.getInstance().setCustomKey("edit_profile_route_accessed", true)
            Timber.d("EditProfileScreen authViewModel: ${authViewModel.hashCode()}")

            when (authState) {
                is AuthState.Authenticated -> {
                    EditProfileScreen(
                        authViewModel = authViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                is AuthState.Loading -> {
                    LoadingStateView()
                }
                else -> {
                    FirebaseCrashlytics.getInstance().log("Edit profile accessed in non-authenticated state: ${authState::class.simpleName}")
                    FirebaseCrashlytics.getInstance().setCustomKey("edit_profile_route_blocked", true)
                    FirebaseCrashlytics.getInstance().setCustomKey("auth_state_at_block", authState::class.simpleName ?: "unknown")
                    Timber.e("Blocked edit_profile route due to non-authenticated state: ${authState::class.simpleName}")
                    navController.navigate("signin") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }
}