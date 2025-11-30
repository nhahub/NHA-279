package com.trailerly.network.mapper

import com.trailerly.model.Actor
import com.trailerly.model.Movie
import com.trailerly.network.dto.CastDto
import com.trailerly.network.dto.CreditsResponse
import com.trailerly.network.dto.MovieDetailsDto
import com.trailerly.network.dto.MovieDto
import com.trailerly.util.TmdbConstants.genreMap
import com.trailerly.util.TmdbImageHelper

/**
 * Mapper object for converting TMDB DTOs to domain models.
 * Handles field mapping and data transformations.
 */
object MovieMapper {

    /**
     * Converts MovieDto to domain Movie model.
     * Maps TMDB fields to app's domain structure.
     */
    fun MovieDto.toDomainModel(): Movie {
        val genre = genreIds.firstOrNull()?.let { genreMap[it] } ?: "Unknown"
        val posterUrl = TmdbImageHelper.getPosterUrl(posterPath) ?: ""
        val backdropUrl = TmdbImageHelper.getBackdropUrl(backdropPath)
        val rating = (voteAverage / 2.0).toFloat()
        val releaseYear = extractYear(releaseDate)

        return Movie(
            id = id,
            title = title,
            posterUrl = posterUrl,
            rating = rating,
            genre = genre,
            description = overview ?: "",
            actors = emptyList(), // Will be populated from credits endpoint
            backdropUrl = backdropUrl,
            voteAverage = voteAverage,
            genreIds = genreIds,
            releaseYear = releaseYear,
            originalTitle = originalTitle,
            originalLanguage = originalLanguage
        )
    }

    private fun extractYear(releaseDate: String?): String? {
        if (releaseDate.isNullOrBlank()) return null
        if (releaseDate.length < 4) return null
        val yearString = releaseDate.take(4)
        val year = yearString.toIntOrNull() ?: return null
        if (year < 1888 || year > 2100) return null
        return yearString
    }

    /**
     * Converts MovieDetailsDto to domain Movie model with additional details.
     */
    fun MovieDetailsDto.toDomainModel(): Movie {
        val releaseYear = extractYear(releaseDate)
        val genre = genres.firstOrNull()?.id?.let { genreMap[it] } ?: "Unknown"
        val posterUrl = TmdbImageHelper.getPosterUrl(posterPath) ?: ""
        val backdropUrl = TmdbImageHelper.getBackdropUrl(backdropPath)
        val rating = (voteAverage / 2.0).toFloat()

        return Movie(
            id = id,
            title = title,
            posterUrl = posterUrl,
            rating = rating,
            genre = genre,
            description = overview ?: "",
            actors = emptyList(), // Will be populated separately
            backdropUrl = backdropUrl,
            voteAverage = voteAverage,
            genreIds = genres.map { it.id },
            releaseYear = releaseYear,
            originalTitle = originalTitle,
            originalLanguage = originalLanguage
        )
    }

    /**
     * Converts CastDto to domain Actor model.
     */
    fun CastDto.toActor(): Actor {
        val profileImageUrl = TmdbImageHelper.getProfileUrl(profilePath)
        return Actor(
            id = id,
            name = name,
            character = character,
            profileImageUrl = profileImageUrl
        )
    }

    /**
     * Combines movie details with credits to create complete Movie model.
     */
    fun MovieDetailsDto.toDomainModel(credits: CreditsResponse): Movie {
        val movie = toDomainModel()
        val actors = credits.cast.take(5).map { it.toActor() } // Top 5 cast members

        return movie.copy(actors = actors)
    }

    /**
     * Combines movie details with credits and optional English details to create complete Movie model.
     */
    fun MovieDetailsDto.toDomainModel(credits: CreditsResponse, englishDetails: MovieDetailsDto?): Movie {
        val movie = toDomainModel(credits)
        val displayGenre = genres.firstOrNull()?.id?.let { genreMap[it] }
            ?: englishDetails?.genres?.firstOrNull()?.name
            ?: "Unknown"
        return movie.copy(
            genre = displayGenre,
            englishTitle = englishDetails?.title,
            englishDescription = englishDetails?.overview,
            originalDescription = this.overview
        )
    }
}
