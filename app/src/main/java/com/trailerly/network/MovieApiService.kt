package com.trailerly.network

import com.trailerly.network.dto.CreditsResponse
import com.trailerly.network.dto.MovieDetailsDto
import com.trailerly.network.dto.MovieListResponse
import com.trailerly.network.dto.VideosResponse
import com.trailerly.util.TmdbConstants
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MovieApiService {

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE,
        @Query("page") page: Int = 1
    ): MovieListResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE,
        @Query("page") page: Int = 1,
        @Query("with_genres") genreIds: String? = null,
        @Query("sort_by") sortBy: String = "vote_average.desc",
        @Query("vote_count.gte") voteCountGte: Int? = null
    ): MovieListResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): MovieListResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE
    ): MovieDetailsDto

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(@Path("movie_id") movieId: Int): CreditsResponse

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE
    ): VideosResponse

    @GET("discover/movie")
    suspend fun getTodaysMovieReleases(
        @Query("primary_release_date.gte") releaseDateGte: String,
        @Query("primary_release_date.lte") releaseDateLte: String,
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE,
        @Query("page") page: Int = 1
    ): MovieListResponse

    @GET("movie/{movie_id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = TmdbConstants.DEFAULT_LANGUAGE,
        @Query("page") page: Int = 1
    ): MovieListResponse

    @GET("configuration")
    suspend fun getConfiguration(): Response<Any>
}
