package com.example.movieapp.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthResult
import kotlinx.coroutines.tasks.await
import kotlin.Result

object AuthManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result: AuthResult = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user ?: throw Exception("Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result: AuthResult = auth.signInWithCredential(credential).await()
            Result.success(result.user ?: throw Exception("Google sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInAsGuest(): Result<FirebaseUser> {
        return try {
            val result: AuthResult = auth.signInAnonymously().await()
            Result.success(result.user ?: throw Exception("Anonymous sign-in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}