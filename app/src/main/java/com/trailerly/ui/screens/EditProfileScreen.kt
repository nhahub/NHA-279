package com.trailerly.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trailerly.R
import com.trailerly.viewmodel.AuthViewModel // Assuming AuthViewModel handles profile updates
import com.trailerly.model.User // Assuming User data class exists
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.foundation.background
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trailerly.ui.components.LoadingStateView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.LocalIndication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser = authViewModel.currentUser.collectAsState(initial = null).value // Get current user from AuthViewModel

    var name by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    Timber.d("EditProfileScreen opened - currentUser: ${currentUser?.uid}")
    FirebaseCrashlytics.getInstance().setCustomKey("edit_profile_screen_opened", true)
    if (currentUser == null) {
        Timber.w("EditProfileScreen opened with null currentUser")
    }

    LaunchedEffect(currentUser) {
        FirebaseCrashlytics.getInstance().setCustomKey("current_user_loaded", currentUser != null)
        if (currentUser != null && name.isEmpty()) {
            Timber.d("EditProfileScreen: currentUser loaded, initializing name field")
            name = currentUser.name
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImageUri = uri
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val profileUpdateResult by authViewModel.profileUpdateResult.collectAsState(initial = null)
    val isLoading = profileUpdateResult is com.trailerly.auth.AuthResult.Loading

    when (profileUpdateResult) {
        is com.trailerly.auth.AuthResult.Success -> {
            LaunchedEffect(profileUpdateResult) {
                snackbarHostState.showSnackbar("Profile updated successfully!")
            }
            authViewModel.clearProfileUpdateResult()
            onNavigateBack()
        }
        is com.trailerly.auth.AuthResult.Error -> {
            LaunchedEffect(profileUpdateResult) {
                snackbarHostState.showSnackbar("Error: ${(profileUpdateResult as com.trailerly.auth.AuthResult.Error).message}")
            }
            authViewModel.clearProfileUpdateResult()
        }
        else -> {}
    }

    if (currentUser == null) {
        Timber.w("EditProfileScreen: Waiting for currentUser to load")
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Edit Profile") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            },
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
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Picture
            val profilePictureUri = selectedImageUri
            val existingPhotoUrl = currentUser?.profilePictureUrl
            Box(
                modifier = Modifier.size(140.dp), // Increased to accommodate the button
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        )
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePictureUri != null || existingPhotoUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = profilePictureUri ?: existingPhotoUrl,
                            contentDescription = "Profile picture",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.ic_person),
                            error = painterResource(R.drawable.ic_person)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default profile picture",
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                IconButton(
                    onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit profile picture",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Other fields could go here (e.g., profile picture URL, etc.)

            Spacer(modifier = Modifier.weight(1f)) // Pushes buttons to the bottom

            // Save Button
            Button(
                onClick = {
                    if (isLoading) return@Button
                    Timber.d("Save Changes clicked - User: ${currentUser?.uid}, Name: $name, ImageSelected: ${selectedImageUri != null}")
                    FirebaseCrashlytics.getInstance().setCustomKey("save_changes_clicked", true)
                    FirebaseCrashlytics.getInstance().setCustomKey("save_changes_user_id", currentUser?.uid ?: "null")
                    FirebaseCrashlytics.getInstance().setCustomKey("save_changes_image_selected", selectedImageUri != null)
                    val user = currentUser
                    if (user == null) {
                        Timber.e("currentUser is null during profile update")
                        FirebaseCrashlytics.getInstance().recordException(Exception("currentUser became null during profile update"))
                        scope.launch { snackbarHostState.showSnackbar("Error: User not available.") }
                        onNavigateBack()
                        return@Button
                    }
                    val finalName = if (selectedImageUri != null && name.isBlank()) user.name else name
                    if (selectedImageUri != null) {
                        authViewModel.updateUserProfileWithPicture(user.uid, finalName, selectedImageUri)
                    } else {
                        authViewModel.updateUserProfile(user.uid, finalName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = currentUser != null && ((name.isNotBlank() && name != currentUser.name) || selectedImageUri != null) && !isLoading // Enable if user is loaded and either name changed or image selected, and not loading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Changes", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Cancel Button
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
