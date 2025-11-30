package com.trailerly.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object representing a movie from TMDB API v3.
 * This DTO matches TMDB's movie object structure and should be mapped to the domain Movie model.
 */
data class MovieDto(
    val id: Int,
    val title: String,
    @Json(name = "original_title") val originalTitle: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double,
    @Json(name = "vote_count") val voteCount: Int,
    val popularity: Double,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "genre_ids") val genreIds: List<Int>,
    val adult: Boolean,
    val video: Boolean,
    @Json(name = "original_language") val originalLanguage: String?
)
