package com.trailerly.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object representing detailed movie information from TMDB API.
 * Used by the /movie/{id} endpoint for complete movie details.
 */
data class MovieDetailsDto(
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
    val runtime: Int?,
    val budget: Long,
    val revenue: Long,
    val status: String?,
    val tagline: String?,
    val homepage: String?,
    @Json(name = "imdb_id") val imdbId: String?,
    val genres: List<GenreDto>,
    @Json(name = "production_companies") val productionCompanies: List<ProductionCompanyDto>?,
    @Json(name = "spoken_languages") val spokenLanguages: List<SpokenLanguageDto>?,
    val adult: Boolean,
    val video: Boolean,
    @Json(name = "original_language") val originalLanguage: String?
)

/**
 * Nested DTO for movie genres.
 */
data class GenreDto(
    val id: Int,
    val name: String
)

/**
 * Nested DTO for production companies.
 */
data class ProductionCompanyDto(
    val id: Int,
    val name: String,
    @Json(name = "logo_path") val logoPath: String?,
    @Json(name = "origin_country") val originCountry: String?
)

/**
 * Nested DTO for spoken languages.
 */
data class SpokenLanguageDto(
    @Json(name = "iso_639_1") val iso6391: String,
    val name: String
)
