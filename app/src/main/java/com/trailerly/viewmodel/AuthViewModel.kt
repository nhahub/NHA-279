package com.trailerly.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailerly.auth.AuthResult
import com.trailerly.auth.AuthState
import com.trailerly.data.PreferencesRepository
import com.trailerly.model.User
import com.trailerly.repository.AuthRepository
import com.trailerly.util.NetworkMonitor
import com.trailerly.util.ValidationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.tasks.await

/**
 * ViewModel that manages authentication state and operations.
 * Provides reactive auth state and handles sign in/up operations with validation.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(getInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult.asStateFlow()

    // Network connectivity state shared with timeout handling
    val isOnline = networkMonitor.connectivityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = networkMonitor.isConnected()
        )

    private fun getInitialAuthState(): AuthState {
        val user = authRepository.getCurrentUser()
        return if (user != null) {
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
    }

    // Flow to expose the current user\'s data to the UI
    val currentUser: StateFlow<User?> = authState.map {
        val userId = if (it is AuthState.Authenticated) it.userId else null
        Timber.d("currentUser flow emitting: ${userId ?: "null"}")
        if (it is AuthState.Authenticated) {
            User(
                uid = it.userId,
                name = it.displayName ?: it.email?.substringBefore('@') ?: "User",
                email = it.email ?: "N/A",
                initials = it.displayName?.split(" ")?.mapNotNull { it.firstOrNull() }?.joinToString("")?.uppercase() ?: it.email?.firstOrNull()?.toString()?.uppercase() ?: "?",
                profilePictureUrl = it.photoUrl
            )
        } else {
            null
        }
    }.stateIn( // Changed from .asStateFlow() to .stateIn()
        viewModelScope,
        SharingStarted.Lazily,
        null
    )

    private val _profileUpdateResult = MutableSharedFlow<AuthResult?>(replay = 0)
    val profileUpdateResult: SharedFlow<AuthResult?> = _profileUpdateResult

    init {
        // Update offline state based on network connectivity
        viewModelScope.launch {
            networkMonitor.connectivityFlow.collect { isConnected ->
                _isOffline.value = isConnected.not()
            }
        }

        viewModelScope.launch {
            authRepository.getAuthStateFlow().collect { authState ->
                _authState.update { authState }
                // Update guest user preference based on auth state
                if (authState is AuthState.Authenticated) {
                    preferencesRepository.setGuestUser(authState.isAnonymous)
                    FirebaseCrashlytics.getInstance().setUserId(authState.userId)
                    FirebaseCrashlytics.getInstance().setCustomKey("user_email", authState.email ?: "")
                    FirebaseCrashlytics.getInstance().setCustomKey("user_display_name", authState.displayName ?: "")
                    FirebaseCrashlytics.getInstance().setCustomKey("is_anonymous", authState.isAnonymous)
                } else if (authState is AuthState.Unauthenticated) {
                    FirebaseCrashlytics.getInstance().setUserId("")
                    FirebaseCrashlytics.getInstance().setCustomKey("user_email", "")
                    FirebaseCrashlytics.getInstance().setCustomKey("user_display_name", "")
                    FirebaseCrashlytics.getInstance().setCustomKey("is_anonymous", false)
                }
            }
        }
    }

    /**
     * Signs in with email and password after validation.
     */
    fun signInWithEmail(email: String, password: String) {
        FirebaseCrashlytics.getInstance().log("Attempting email sign in")
        if (!validateCredentials(email, password)) return

        if (isOffline.value) {
            _authResult.value = AuthResult.Error("No internet connection. Please check your network and try again.")
            return
        }

        _authResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                // If user is currently anonymous (guest), sign out first
                val currentUser = authRepository.getCurrentUser()
                if (currentUser?.isAnonymous == true) {
                    FirebaseCrashlytics.getInstance().log("Signing out anonymous user before email sign in")
                    authRepository.signOut()
                }

                val result = authRepository.signInWithEmail(email, password)
                _authResult.value = result
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "sign_in_email")
                FirebaseCrashlytics.getInstance().recordException(e)
                _authResult.value = AuthResult.Error("An unexpected error occurred. Please try again.")
            }
        }
    }

    /**
     * Signs up with email and password after validation.
     */
    fun signUpWithEmail(email: String, password: String, confirmPassword: String) {
        FirebaseCrashlytics.getInstance().log("Attempting email sign up")
        if (!validateCredentials(email, password)) return

        if (!ValidationUtils.doPasswordsMatch(password, confirmPassword)) {
            _authResult.value = AuthResult.Error("Passwords do not match")
            return
        }

        if (isOffline.value) {
            _authResult.value = AuthResult.Error("No internet connection. Please check your network and try again.")
            return
        }

        _authResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                // If user is currently anonymous (guest), sign out first
                val currentUser = authRepository.getCurrentUser()
                if (currentUser?.isAnonymous == true) {
                    FirebaseCrashlytics.getInstance().log("Signing out anonymous user before email sign up")
                    authRepository.signOut()
                }

                val result = authRepository.signUpWithEmail(email, password)
                _authResult.value = result
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "sign_up_email")
                FirebaseCrashlytics.getInstance().recordException(e)
                _authResult.value = AuthResult.Error("An unexpected error occurred. Please try again.")
            }
        }
    }

    /**
     * Signs in anonymously (guest mode).
     */
    fun signInAnonymously() {
        FirebaseCrashlytics.getInstance().log("Attempting anonymous sign in")
        if (isOffline.value) {
            _authResult.value = AuthResult.Error("No internet connection. Please check your network and try again.")
            return
        }

        _authResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                val result = authRepository.signInAnonymously()
                _authResult.value = result
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "sign_in_anonymous")
                FirebaseCrashlytics.getInstance().recordException(e)
                _authResult.value = AuthResult.Error("An unexpected error occurred. Please try again.")
            }
        }
    }

    /**
     * Signs in with Google using an ID token.
     */
    fun signInWithGoogle(idToken: String) {
        FirebaseCrashlytics.getInstance().log("Attempting Google sign in")
        if (isOffline.value) {
            _authResult.value = AuthResult.Error("No internet connection. Please check your network and try again.")
            return
        }

        _authResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                // If user is currently anonymous (guest), sign out first
                val currentUser = authRepository.getCurrentUser()
                if (currentUser?.isAnonymous == true) {
                    FirebaseCrashlytics.getInstance().log("Signing out anonymous user before Google sign in")
                    authRepository.signOut()
                }

                val result = authRepository.signInWithGoogle(idToken)
                _authResult.value = result
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "sign_in_google")
                FirebaseCrashlytics.getInstance().recordException(e)
                _authResult.value = AuthResult.Error("An unexpected error occurred. Please try again.")
            }
        }
    }

    /**
     * Signs out the current user and clears local data.
     */
    fun signOut() {
        FirebaseCrashlytics.getInstance().log("User sign out")
        viewModelScope.launch {
            try {
                authRepository.signOut()
                preferencesRepository.clearUserData()
                // Clear authResult after successful sign out to prevent immediate re-login
                _authResult.value = null
                FirebaseCrashlytics.getInstance().setUserId("")
                FirebaseCrashlytics.getInstance().setCustomKey("user_email", "")
                FirebaseCrashlytics.getInstance().setCustomKey("user_display_name", "")
                FirebaseCrashlytics.getInstance().setCustomKey("is_anonymous", false)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "sign_out")
                FirebaseCrashlytics.getInstance().recordException(e)
                _authResult.value = AuthResult.Error("An unexpected error occurred during sign out. Please try again.")
            }
        }
    }

    /**
     * Clears the current auth result (for dismissing error messages).
     */
    fun clearAuthResult() {
        _authResult.update { null }
    }

    /**
     * Sets the auth result (for error handling in UI).
     */
    fun setAuthResult(result: AuthResult) {
        _authResult.value = result
    }

    /**
     * Updates the user\'s profile.
     * @param userId The UID of the user to update. (Firebase usually updates current user so userId might not be strictly needed here, but kept for consistency if future multi-user management is planned)
     * @param newName The new display name for the user.
     */
    fun updateUserProfile(userId: String, newName: String) {
        FirebaseCrashlytics.getInstance().log("Updating user profile for userId: $userId with name: $newName")
        if (isOffline.value) {
            _profileUpdateResult.tryEmit(AuthResult.Error("No internet connection. Please check your network and try again."))
            return
        }

        _profileUpdateResult.tryEmit(AuthResult.Loading)
        viewModelScope.launch {
            try {
                // The userId parameter is currently not used by authRepository.updateUserProfile
                // as Firebase updates the *current* authenticated user. This parameter is kept
                // for potential future use if multiple user profiles are managed.
                val result = authRepository.updateUserProfile(newName)

                // After successful update, refresh auth state to update currentUser flow
                if (result is AuthResult.Success) {
                    FirebaseCrashlytics.getInstance().log("Profile update successful, refreshing auth state")
                    authRepository.getCurrentUser()?.reload()?.await() // Force refresh FirebaseUser object
                    val refreshedUser = authRepository.getCurrentUser()
                    if (refreshedUser != null) {
                        val newAuthState = AuthState.Authenticated(
                            userId = refreshedUser.uid,
                            email = refreshedUser.email,
                            displayName = refreshedUser.displayName,
                            isAnonymous = refreshedUser.isAnonymous,
                            photoUrl = refreshedUser.photoUrl?.toString()
                        )
                        _authState.value = newAuthState
                        FirebaseCrashlytics.getInstance().log("Auth state refreshed with updated profile data")
                    }
                    _profileUpdateResult.tryEmit(AuthResult.Success)
                } else {
                    _profileUpdateResult.tryEmit(result)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "update_profile")
                FirebaseCrashlytics.getInstance().recordException(e)
                _profileUpdateResult.tryEmit(AuthResult.Error("An unexpected error occurred. Please try again."))
            }
        }
    }

    /**
     * Updates the user\'s profile with optional picture.
     * @param userId The UID of the user to update.
     * @param newName The new display name for the user.
     * @param imageUri The URI of the new profile picture, or null if not changing.
     */
    fun updateUserProfileWithPicture(userId: String, newName: String, imageUri: android.net.Uri?) {
        FirebaseCrashlytics.getInstance().log("Updating user profile for userId: $userId with name: $newName and photo")
        if (isOffline.value) {
            _profileUpdateResult.tryEmit(AuthResult.Error("No internet connection. Please check your network and try again."))
            return
        }

        _profileUpdateResult.tryEmit(AuthResult.Loading)
        viewModelScope.launch {
            try {
                var photoUrl: String? = null
                if (imageUri != null) {
                    FirebaseCrashlytics.getInstance().log("Uploading profile picture for userId: $userId")
                    val uploadResult = authRepository.uploadProfilePicture(imageUri)
                    if (uploadResult.isFailure) {
                        _profileUpdateResult.tryEmit(AuthResult.Error(uploadResult.exceptionOrNull()?.message ?: "Failed to upload profile picture"))
                        return@launch
                    }
                    photoUrl = uploadResult.getOrNull()
                    FirebaseCrashlytics.getInstance().log("Profile picture uploaded, URL: $photoUrl")
                }

                FirebaseCrashlytics.getInstance().log("Updating user profile with name: $newName and photoUrl: $photoUrl")
                val result = authRepository.updateUserProfile(newName, photoUrl)

                // After successful update, refresh auth state to update currentUser flow
                if (result is AuthResult.Success) {
                    FirebaseCrashlytics.getInstance().log("Profile update successful, refreshing auth state")
                    authRepository.getCurrentUser()?.reload()?.await() // Force refresh FirebaseUser object
                    val refreshedUser = authRepository.getCurrentUser()
                    if (refreshedUser != null) {
                        val newAuthState = AuthState.Authenticated(
                            userId = refreshedUser.uid,
                            email = refreshedUser.email,
                            displayName = refreshedUser.displayName,
                            isAnonymous = refreshedUser.isAnonymous,
                            photoUrl = refreshedUser.photoUrl?.toString()
                        )
                        _authState.value = newAuthState
                        FirebaseCrashlytics.getInstance().log("Auth state refreshed with updated profile data")
                    }
                    _profileUpdateResult.tryEmit(AuthResult.Success)
                } else {
                    _profileUpdateResult.tryEmit(result)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().setCustomKey("auth_operation", "update_profile_with_picture")
                FirebaseCrashlytics.getInstance().recordException(e)
                _profileUpdateResult.tryEmit(AuthResult.Error("An unexpected error occurred. Please try again."))
            }
        }
    }

    /**
     * Clears the current profile update result (for dismissing messages).
     */
    fun clearProfileUpdateResult() {
        _profileUpdateResult.tryEmit(null)
    }

    /**
     * Validates email and password inputs.
     * Sets error result if validation fails.
     */
    private fun validateCredentials(email: String, password: String): Boolean {
        val emailError = ValidationUtils.getEmailError(email)
        val passwordError = ValidationUtils.getPasswordError(password)

        return when {
            emailError != null -> {
                _authResult.value = AuthResult.Error(emailError)
                false
            }
            passwordError != null -> {
                _authResult.value = AuthResult.Error(passwordError)
                false
            }
            else -> true
        }
    }
}
