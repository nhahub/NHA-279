package com.trailerly.util

import com.trailerly.BuildConfig
import com.trailerly.network.YouTubeApiClient
import com.trailerly.network.dto.VideoDto
import com.trailerly.network.dto.YouTubeSearchItem
import com.trailerly.network.dto.YouTubeVideoDetailsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.parseIsoStringOrNull

/**
 * Utility object for handling YouTube video operations and filtering.
 * Provides helper functions to extract YouTube video IDs and find official trailers.
 */
object YouTubeHelper {

    /**
     * Extracts the YouTube video ID from a VideoDto if it's from YouTube.
     * @param videoDto The video data transfer object
     * @return The YouTube video key if the video is from YouTube, null otherwise
     */
    fun extractYouTubeVideoId(videoDto: VideoDto): String? {
        return if (videoDto.site == "YouTube") videoDto.key else null
    }

    /**
     * Finds the most relevant trailer or teaser from a list of videos.
     * Prioritizes official YouTube trailers, then any YouTube trailers, then official teasers, then any teasers.
     * Also considers keywords in the video name for better relevance.
     * @param videos List of video DTOs from TMDB
     * @return The best trailer or teaser video, or null if no suitable video is found
     */
    fun getOfficialTrailer(videos: List<VideoDto>): VideoDto? {
        val youtubeVideos = videos.filter { it.site == "YouTube" }

        // 1. Official Trailers
        val officialTrailers = youtubeVideos.filter { it.type == "Trailer" && it.official }
        if (officialTrailers.isNotEmpty()) {
            return officialTrailers.firstOrNull { it.name.contains("Official Trailer", ignoreCase = true) } ?: officialTrailers.first()
        }

        // 2. Any Trailers
        val anyTrailers = youtubeVideos.filter { it.type == "Trailer" }
        if (anyTrailers.isNotEmpty()) {
            return anyTrailers.firstOrNull { it.name.contains("Trailer", ignoreCase = true) } ?: anyTrailers.first()
        }

        // 3. Official Teasers
        val officialTeasers = youtubeVideos.filter { it.type == "Teaser" && it.official }
        if (officialTeasers.isNotEmpty()) {
            return officialTeasers.firstOrNull { it.name.contains("Official Teaser", ignoreCase = true) } ?: officialTeasers.first()
        }

        // 4. Any Teasers
        val anyTeasers = youtubeVideos.filter { it.type == "Teaser" }
        if (anyTeasers.isNotEmpty()) {
            return anyTeasers.firstOrNull { it.name.contains("Teaser", ignoreCase = true) } ?: anyTeasers.first()
        }

        // No suitable trailer or teaser found
        return null
    }

    /**
     * Searches YouTube for a movie trailer as a fallback mechanism.
     *
     * This function is called when an official trailer is not found on TMDb. It constructs a
     * search query with the movie title, year, and "official trailer" and calls the YouTube Data API.
     * It selects the top-viewed video under 4 minutes whose title contains the movie name,
     * from official or verified channels.
     * If a result is found, it's converted into a synthetic [VideoDto] for compatibility
     * with the existing UI and data layers.
     *
     * @param movieTitle The title of the movie to search for.
     * @param movieYear The release year of the movie (optional, improves search accuracy).
     * @return A [VideoDto] representing the found YouTube trailer, or null if the API key is
     * missing, no results are found, or an error occurs.
     */
    suspend fun searchYouTubeTrailer(movieTitle: String, movieYear: Int? = null): VideoDto? {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            Timber.w("YouTube API key not configured, skipping YouTube search.")
            return null
        }

        // Build multiple search queries to try different approaches
        val yearString = movieYear?.toString() ?: ""
        val searchQueries = listOf(
            "$movieTitle $yearString official trailer",  // Primary: with "official"
            "$movieTitle $yearString trailer",           // Secondary: without "official"
            "$movieTitle official trailer",              // Fallback: without year
            "$movieTitle trailer"                        // Last resort: basic trailer search
        )

        for (searchQuery in searchQueries) {
            Timber.d("Searching YouTube for trailer: '%s'", searchQuery)

            try {
                val response = YouTubeApiClient.youtubeApiService.searchVideos(
                    query = searchQuery,
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                    maxResults = 10 // Increase to get more candidates for filtering
                )

                Timber.d("YouTube API returned %d items for query: '%s'", response.items.size, searchQuery)

                // Filter results where title contains movie name (case-insensitive)
                val filteredItems = response.items.filter { item ->
                    val title = item.snippet?.title?.lowercase() ?: ""
                    val movieNameLower = movieTitle.lowercase()

                    // Check if title contains movie name
                    title.contains(movieNameLower)
                }

                Timber.d("Filtered to %d items containing movie name '%s'", filteredItems.size, movieTitle)

                if (filteredItems.isEmpty()) {
                    Timber.d("No videos found with movie name in title for query: '%s', trying next query", searchQuery)
                    continue
                }

                // Get video IDs for details fetch
                val videoIds = filteredItems.mapNotNull { it.id?.videoId }.joinToString(",")

                if (videoIds.isBlank()) {
                    Timber.d("No valid video IDs found for query: '%s', trying next query", searchQuery)
                    continue
                }

                // Fetch video details (duration and view count)
                val detailsResponse = YouTubeApiClient.youtubeApiService.getVideoDetails(
                    id = videoIds,
                    apiKey = BuildConfig.YOUTUBE_API_KEY
                )

                Timber.d("Fetched details for %d videos", detailsResponse.items.size)

                // Filter by duration < 4 minutes and sort by view count descending
                val validTrailers = detailsResponse.items
                    .mapNotNull { details ->
                        val searchItem = filteredItems.find { it.id?.videoId == details.id }
                        if (searchItem != null) {
                            Pair(searchItem, details)
                        } else null
                    }
                    .filter { (_, details) ->
                        val duration = parseDuration(details.contentDetails?.duration)
                        duration != null && duration < Duration.parse("4m")
                    }
                    .sortedByDescending { (_, details) ->
                        details.statistics?.viewCount?.toLongOrNull() ?: 0L
                    }

                Timber.d("Found %d valid trailers under 4 minutes", validTrailers.size)

                // Take the top-viewed one
                val bestPair = validTrailers.firstOrNull()
                if (bestPair == null) {
                    Timber.d("No suitable YouTube video found after filtering for query: '%s', trying next query", searchQuery)
                    continue
                }
                val (bestItem, bestDetails) = bestPair

                if (bestItem != null && bestDetails != null) {
                    val videoId = bestItem.id?.videoId ?: ""
                    Timber.i("Selected top YouTube trailer: %s (title: '%s', views: %s, duration: %s)",
                           videoId, bestItem.snippet?.title, bestDetails.statistics?.viewCount, bestDetails.contentDetails?.duration)

                    // Get the best thumbnail URL (prefer high quality)
                    val thumbnailUrl = bestItem.snippet?.thumbnails?.run {
                        high?.url ?: medium?.url ?: default?.url
                    }

                    // Create a synthetic VideoDto to represent the YouTube search result
                    return VideoDto(
                        id = "youtube_fallback_$videoId",
                        key = videoId,
                        name = bestItem.snippet?.title ?: "$movieTitle $yearString - Trailer",
                        site = "YouTube",
                        type = "Trailer",
                        size = 1080, // Default to a common HD resolution
                        official = bestItem.snippet?.channelId in TmdbConstants.OFFICIAL_YOUTUBE_CHANNELS, // Mark as official if from verified channel
                        publishedAt = null,
                        iso6391 = null,
                        iso31661 = null,
                        thumbnail = thumbnailUrl
                    )
                } else {
                    Timber.d("No suitable YouTube video found after filtering for query: '%s', trying next query", searchQuery)
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 403) {
                    Timber.w("YouTube API quota exceeded for query '%s', falling back to search link", searchQuery)
                    // Return a synthetic VideoDto for search fallback
                    return VideoDto(
                        id = "youtube_search_fallback",
                        key = "youtube_search",
                        name = searchQuery,
                        site = "YouTube",
                        type = "Trailer",
                        size = 1080,
                        official = false,
                        publishedAt = null,
                        iso6391 = null,
                        iso31661 = null,
                        thumbnail = null
                    )
                } else {
                    Timber.w(e, "YouTube search failed for query '%s': %s", searchQuery, e.message)
                    // Continue to next query instead of failing completely
                }
            } catch (e: Exception) {
                Timber.w(e, "YouTube search failed for query '%s': %s", searchQuery, e.message)
                // Continue to next query instead of failing completely
            }
        }

        Timber.d("No YouTube trailer found for movie: '%s' after trying all queries", movieTitle)
        return null
    }

    /**
     * Parses ISO 8601 duration string to Duration object.
     * @param durationString ISO 8601 duration string (e.g., "PT4M13S")
     * @return Duration object or null if parsing fails
     */
    private fun parseDuration(durationString: String?): Duration? {
        return durationString?.let { parseIsoStringOrNull(it) }
    }

    /**
     * Builds a full YouTube URL from a video key for external sharing.
     * @param videoKey The YouTube video ID
     * @return Full YouTube URL
     */
    fun buildYouTubeUrl(videoKey: String): String {
        return "https://www.youtube.com/watch?v=$videoKey"
    }

    /**
     * Builds a YouTube search URL from a query string.
     * @param query The search query
     * @return YouTube search URL
     */
    fun buildYouTubeSearchUrl(query: String): String {
        return "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}"
    }
}