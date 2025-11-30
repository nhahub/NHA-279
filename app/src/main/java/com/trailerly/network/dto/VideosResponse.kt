package com.trailerly.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object representing movie videos from TMDB API.
 * Used by the /movie/{id}/videos endpoint. The 'key' field is used to construct YouTube URLs.
 */
data class VideosResponse(
    val id: Int,
    val results: List<VideoDto>
)

/**
 * Nested DTO for video metadata.
 */
data class VideoDto(
    val id: String,
    val key: String, // YouTube video ID
    val name: String,
    val site: String, // e.g., "YouTube"
    val type: String, // e.g., "Trailer", "Teaser"
    val size: Int, // e.g., 1080, 720
    val official: Boolean,
    @Json(name = "published_at") val publishedAt: String?,
    @Json(name = "iso_639_1") val iso6391: String?,
    @Json(name = "iso_3166_1") val iso31661: String?,
    val thumbnail: String? // URL to video thumbnail, added for YouTube fallback
)
