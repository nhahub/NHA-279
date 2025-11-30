package com.trailerly.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailerly.auth.AuthState
import com.trailerly.data.PreferencesRepository
import com.trailerly.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository // Inject AuthRepository
) : ViewModel() {

    // Removed private val authRepository = AuthRepository() as it's now injected
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    init {
        loadPreferences()
        observeAuthState()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesRepository.getDarkMode()
                .collect { darkModeValue ->
                    _darkMode.value = darkModeValue
                }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.getAuthStateFlow().collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        if (!authState.isAnonymous) {
                            try {
                                val result = preferencesRepository.fetchAndMergePreferencesFromFirestore(authState.userId)
                                if (result.isSuccess) {
                                    Timber.d("Successfully synced preferences from Firestore on login")
                                } else {
                                    Timber.e("Failed to sync preferences from Firestore on login: ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e: Exception) {
                                Timber.e("Error syncing preferences from Firestore on login: ${e.message}", e)
                            }
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        // No action needed - local preferences persist
                    }
                    else -> {
                        // Handle other states if needed
                    }
                }
            }
        }
    }

    fun onDarkModeChange(isDarkMode: Boolean) {
        _darkMode.value = isDarkMode
        viewModelScope.launch {
            try {
                preferencesRepository.setDarkMode(isDarkMode)
            } catch (e: Exception) {
                // Handle error gracefully
            }
        }
    }

    // Removed retryPreferencesSync - no longer needed with direct Firestore operations

    fun onLogout() {
        viewModelScope.launch {
            preferencesRepository.clearUserData() // Clear user-specific preferences
            authRepository.signOut() // Call signOut from the injected AuthRepository
        }
    }
}