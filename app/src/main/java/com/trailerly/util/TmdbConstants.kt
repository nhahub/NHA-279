package com.trailerly.util

import java.util.Locale

/**
 * Constants for TMDB API integration.
 * Contains official TMDB constants and configuration values.
 */
object TmdbConstants {

    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val DEFAULT_LANGUAGE = "en-US"
    const val DEFAULT_PAGE = 1

    /**
     * Set of TMDb-supported language tags (BCP 47 format).
     */
    private val supportedLanguageTags = setOf(
        "en-US", "en-GB", "en-CA", "en-AU",
        "es-ES", "es-MX", "es-AR", "es-CO",
        "fr-FR", "fr-CA",
        "de-DE", "de-AT", "de-CH",
        "it-IT",
        "pt-BR", "pt-PT",
        "ru-RU",
        "ja-JP",
        "ko-KR",
        "zh-CN", "zh-TW",
        "hi-IN",
        "ar-SA",
        "tr-TR",
        "nl-NL",
        "sv-SE",
        "da-DK",
        "no-NO",
        "fi-FI",
        "pl-PL",
        "cs-CZ",
        "hu-HU",
        "ro-RO",
        "sk-SK",
        "sl-SI",
        "hr-HR",
        "bg-BG",
        "uk-UA",
        "el-GR",
        "he-IL",
        "th-TH",
        "vi-VN",
        "id-ID",
        "ms-MY",
        "tl-PH"
    )

    /**
     * Resolves the preferred language code from the current locale.
     * Maps Locale to a TMDb-accepted language code, preferring region-specific tags (language-country) when known,
     * with fallbacks to language and DEFAULT_LANGUAGE.
     */
    fun getPreferredLanguage(): String {
        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag()

        // Prefer region-specific tag if supported
        if (languageTag in supportedLanguageTags) {
            return languageTag
        }

        // Fallback to language-only if supported
        val languageOnly = locale.language
        if (supportedLanguageTags.any { it.startsWith("$languageOnly-") }) {
            return languageOnly
        }

        // Final fallback
        return DEFAULT_LANGUAGE
    }

    // Sorting and filtering constants
    const val SORT_BY_RATING = "vote_average.desc"
    const val MIN_VOTE_COUNT = 100
    const val RANDOM_PAGE_MIN = 1
    const val RANDOM_PAGE_MAX = 5

    // Genre ID constants
    const val GENRE_ACTION = 28
    const val GENRE_COMEDY = 35
    const val GENRE_DRAMA = 18
    const val GENRE_ROMANCE = 10749
    const val GENRE_SCIFI = 878
    const val GENRE_THRILLER = 53
    const val GENRE_HORROR = 27
    const val GENRE_ANIMATION = 16

    /**
     * Map of TMDB genre IDs to genre names.
     */
    val genreMap = mapOf(
        GENRE_ACTION to "Action",
        GENRE_COMEDY to "Comedy",
        GENRE_DRAMA to "Drama",
        GENRE_ROMANCE to "Romance",
        GENRE_SCIFI to "Sci-Fi",
        GENRE_THRILLER to "Thriller",
        GENRE_HORROR to "Horror",
        GENRE_ANIMATION to "Animation",
        99 to "Documentary",
        10751 to "Family",
        12 to "Adventure",
        80 to "Crime",
        14 to "Fantasy",
        36 to "History",
        10402 to "Music",
        9648 to "Mystery",
        10752 to "War",
        37 to "Western",
        10770 to "TV Movie"
    )

    // YouTube API constants
    const val YOUTUBE_BASE_URL = "https://www.googleapis.com/"
    const val YOUTUBE_SEARCH_MAX_RESULTS = 5 // Increased to allow filtering for official channels
    const val YOUTUBE_SEARCH_TYPE = "video"
    const val YOUTUBE_SEARCH_PART = "id,snippet"
    const val YOUTUBE_SEARCH_EMBEDDABLE = "true"

    // Official YouTube channels for movie trailers (channel IDs)
    val OFFICIAL_YOUTUBE_CHANNELS = setOf(
        "UCWOA1ZGywLbqmigxE4Qlvuw", // Netflix
        "UCx-KWLTKlB83hDI6UKECtJQ", // ViX
        "UCjmJDM5pRKbUlVIzDYYWb6g", // Warner Bros. Pictures
        "UC2-BeLxzUBSs0uSrmzWhJuQ", // Sony Pictures Entertainment
        "UCq0OueAsdxH6b8nyAspp9dw", // Universal Pictures
        "UCi-_hzGZtABcRGhkwGHQ3nw", // Paramount Pictures
        "UCz97F7dMxBNOfGYu3rx8aCw", // Disney
        "UCuaFvcY4MhZY3U43mMt1cfg", // 20th Century Studios
        "UCIIZ9MhFgcnhq4c7F4F4WUw", // Lionsgate Movies
        "UCJ6nMHaJPZvsJ-HmUmj1SeA", // A24
        "UCi7GJNg51C3jgmYTUwqoUXA", // AMC Theatres
        "UC3gNmTGu-TTbFPpfSs_EYIA", // MGM
        "UCpJN7kiUkDrH11p0GQhLyFw", // Focus Features
        "UC_A--fhX5gea0i4UtpD99Gg", // STX Entertainment
        "UCd6MoB9NC6uYN2grvUNT-Zg", // Neon
        "UCJrkNt0mqpWJ5n9R5vPVoJA", // IFC Films
        "UC8rBJQKE5ZfRGWGJYwPJ-A", // Amazon MGM Studios
        "UCBR8-60-B28hp2BmDPdntcQ", // YouTube Movies
        "UCi8e0iOVk1fEOogdfu4YgfA", // Movieclips Trailers
        "UC3gNmTGu-TTbFPpfSs_EYIA"  // MGM (duplicate, but keeping for completeness)
    )
}
