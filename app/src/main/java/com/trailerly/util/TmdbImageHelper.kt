package com.trailerly.util

/**
 * Utility object for constructing TMDB image URLs.
 * Handles null paths gracefully and provides different image sizes.
 */
object TmdbImageHelper {

    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    /**
     * Constructs poster image URL.
     * @param posterPath TMDB poster path (can be null)
     * @param size Image size (w92, w154, w185, w342, w500, w780, original)
     * @return Full image URL or null if posterPath is null
     */
    fun getPosterUrl(posterPath: String?, size: String = "w500"): String? {
        return posterPath?.let { "$IMAGE_BASE_URL$size$it" }
    }

    /**
     * Constructs backdrop image URL.
     * @param backdropPath TMDB backdrop path (can be null)
     * @param size Image size (w300, w780, w1280, original)
     * @return Full image URL or null if backdropPath is null
     */
    fun getBackdropUrl(backdropPath: String?, size: String = "w1280"): String? {
        return backdropPath?.let { "$IMAGE_BASE_URL$size$it" }
    }

    /**
     * Constructs profile image URL for cast/crew.
     * @param profilePath TMDB profile path (can be null)
     * @param size Image size (w45, w185, h632, original)
     * @return Full image URL or null if profilePath is null
     */
    fun getProfileUrl(profilePath: String?, size: String = "w185"): String? {
        return profilePath?.let { "$IMAGE_BASE_URL$size$it" }
    }
}
