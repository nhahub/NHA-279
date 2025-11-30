package com.trailerly.di

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.trailerly.R // Assuming R exists and contains default_web_client_id
import com.trailerly.data.Firestore
import com.trailerly.repository.AuthRepository
import com.trailerly.repository.MovieRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        googleSignInClient: GoogleSignInClient // Inject GoogleSignInClient
    ): AuthRepository {
        return AuthRepository(googleSignInClient)
    }

    @Singleton
    @Provides
    fun provideGoogleSignInClient(
        @ApplicationContext context: Context
    ): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id)) // Assuming default_web_client_id is in strings.xml
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    @Singleton
    @Provides
    fun provideMovieRepository(): MovieRepository {
        return MovieRepository()
    }

    @Singleton
    @Provides
    fun provideFirestore(): Firestore {
        return Firestore()
    }
}
