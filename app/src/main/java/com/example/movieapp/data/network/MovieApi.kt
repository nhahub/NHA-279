package com.example.movieapp.data.network

import com.example.movieapp.data.model.Movie
import com.example.movieapp.data.model.TmdbResponse
import com.example.movieapp.data.model.TrailerResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MovieApi {
    @GET("movie/popular")
    suspend fun getPopularMovies(@Query("api_key") apiKey: String): TmdbResponse<Movie>

    @GET("movie/{id}")
    suspend fun getMovieDetails(@Path("id") movieId: Int, @Query("api_key") apiKey: String): Movie

    @GET("movie/{id}/videos")
    suspend fun getMovieTrailers(@Path("id") movieId: Int, @Query("api_key") apiKey: String): TrailerResponse
}
