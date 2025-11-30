package com.example.movieapp.data.repository

import com.example.movieapp.data.network.MovieApi
import com.example.movieapp.data.network.RetrofitClient
import com.example.movieapp.data.model.Movie
import com.example.movieapp.BuildConfig

object MoviesRepository {
    private val api: MovieApi by lazy { RetrofitClient.createApi<MovieApi>() }

    suspend fun getPopularMovies(): List<Movie> {
        return try {
            val response = api.getPopularMovies(BuildConfig.TMDB_API_KEY)
            response.results
        } catch (e: Exception) {
            emptyList()
        }
    }
}
