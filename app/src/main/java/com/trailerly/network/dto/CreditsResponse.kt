package com.trailerly.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object representing movie credits (cast and crew) from TMDB API.
 * Used by the /movie/{id}/credits endpoint.
 */
data class CreditsResponse(
    val id: Int,
    val cast: List<CastDto>,
    val crew: List<CrewDto>
)

/**
 * Nested DTO for cast members.
 */
data class CastDto(
    val id: Int,
    val name: String,
    @Json(name = "original_name") val originalName: String?,
    val character: String?,
    @Json(name = "profile_path") val profilePath: String?,
    val order: Int,
    @Json(name = "cast_id") val castId: Int?,
    val gender: Int?,
    @Json(name = "known_for_department") val knownForDepartment: String?
)

/**
 * Nested DTO for crew members.
 */
data class CrewDto(
    val id: Int,
    val name: String,
    @Json(name = "original_name") val originalName: String?,
    val job: String?,
    val department: String?,
    @Json(name = "profile_path") val profilePath: String?,
    val gender: Int?
)
