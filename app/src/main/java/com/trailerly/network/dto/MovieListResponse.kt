package com.trailerly.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object representing TMDB's paginated movie list response.
 * Used by endpoints like /movie/popular, /discover/movie, and /search/movie.
 */
data class MovieListResponse(
    val page: Int,
    val results: List<MovieDto>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)
