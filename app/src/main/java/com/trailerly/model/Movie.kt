package com.trailerly.model

data class Movie(
    val id: Int,
    val title: String,
    val posterUrl: String,
    val rating: Float,
    val genre: String,
    val description: String,
    val actors: List<Actor>,
    val backdropUrl: String? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val releaseYear: String? = null,
    val originalTitle: String? = null,
    val originalLanguage: String? = null,
    val englishTitle: String? = null,
    val englishDescription: String? = null,
    val originalDescription: String? = null
)
