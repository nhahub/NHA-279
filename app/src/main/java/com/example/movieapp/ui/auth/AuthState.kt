package com.example.movieapp.ui.auth

import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}