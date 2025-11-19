package com.example.movieapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authManager = AuthManager
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authManager.signInWithEmail(email, password)
            result.onSuccess { user ->
                _authState.value = AuthState.Success(user)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(exception.message ?: "Email sign-in coming soon")
            }
        }
    }
    
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authManager.signInWithGoogle(idToken)
            result.onSuccess { user ->
                _authState.value = AuthState.Success(user)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(exception.message ?: "Google sign-in coming soon")
            }
        }
    }
    
    fun signInAsGuest() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authManager.signInAsGuest()
            result.onSuccess { user ->
                _authState.value = AuthState.Success(user)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(exception.message ?: "Guest sign-in failed")
            }
        }
    }
    
    fun signOut() {
        authManager.signOut()
        _authState.value = AuthState.Idle
    }
}