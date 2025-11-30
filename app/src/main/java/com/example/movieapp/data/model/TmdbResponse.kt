package com.example.movieapp.data.model

import com.squareup.moshi.Json

data class TmdbResponse<T>(
    val page: Int,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int,
    val results: List<T>
)
