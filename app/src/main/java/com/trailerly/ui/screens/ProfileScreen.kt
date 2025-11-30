package com.trailerly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.trailerly.R
import com.trailerly.auth.AuthState
import com.trailerly.ui.components.BottomNavigationBar
import com.trailerly.viewmodel.AuthViewModel
import com.trailerly.viewmodel.ProfileViewModel
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToEditProfile: () -> Unit, // Added new navigation callback
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToSignIn: () -> Unit,
    authViewModel: AuthViewModel
) {
    val user by authViewModel.currentUser.collectAsState() // Use authViewModel.currentUser
    val darkMode by viewModel.darkMode.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val isGuest = authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous

    // Removed explicit InteractionSource as Surface's onClick overload handles it

    // Guest-specific UI
    if (isGuest) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            },
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = 2,
                    onTabSelected = { tab ->
                        when (tab) {
                            0 -> onNavigateToHome()
                            1 -> onNavigateToSaved()
                            2 -> { /* Already on profile */ }
                        }
                    }
                )
            },
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You\'re browsing as a guest",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { onNavigateToSignIn() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.sign_in))
                }
            }
        }
    } else {
        // Regular authenticated user UI
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            },
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = 2,
                    onTabSelected = { tab ->
                        when (tab) {
                            0 -> onNavigateToHome()
                            1 -> onNavigateToSaved()
                            2 -> { /* Already on profile */ }
                        }
                    }
                )
            },
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // User Info
                item {
                    user?.let { user -> // Use the User model from AuthViewModel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (user.profilePictureUrl != null) {
                                AsyncImage(
                                    model = user.profilePictureUrl,
                                    contentDescription = "Profile picture",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(96.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = user.initials,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }

                // Settings Options
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit Profile
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            val interactionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = LocalIndication.current,
                                        role = Role.Button,
                                        onClick = {
                                            Timber.d("Edit Profile clicked - User: ${user?.uid}, AuthState: ${authState::class.simpleName}")
                                            FirebaseCrashlytics.getInstance().setCustomKey("edit_profile_navigation_attempt", true)
                                            FirebaseCrashlytics.getInstance().setCustomKey("user_id_at_navigation", user?.uid ?: "null")
                                            if (authState is AuthState.Authenticated && user != null) {
                                                onNavigateToEditProfile()
                                            } else {
                                                Timber.w("Edit Profile clicked but user is null or not authenticated")
                                                FirebaseCrashlytics.getInstance().log("Edit Profile navigation attempted with null user or non-authenticated state")
                                                FirebaseCrashlytics.getInstance().setCustomKey("edit_profile_navigation_failed", true)
                                                FirebaseCrashlytics.getInstance().setCustomKey("user_id_at_failed_navigation", user?.uid ?: "null")
                                                FirebaseCrashlytics.getInstance().setCustomKey("auth_state_at_failed_navigation", authState::class.simpleName ?: "unknown")
                                            }
                                        }
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Edit Profile",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight, // Corrected syntax here
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Dark Mode Toggle
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (darkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Dark Mode",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = darkMode,
                                    onCheckedChange = { viewModel.onDarkModeChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }



                        Spacer(modifier = Modifier.height(16.dp))

                        // Log Out Button
                        OutlinedButton(
                            onClick = { authViewModel.signOut() }, // Use authViewModel for signOut
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Text(stringResource(R.string.log_out))
                            }
                        }
                    }
                }
            }
        }
    }
}
