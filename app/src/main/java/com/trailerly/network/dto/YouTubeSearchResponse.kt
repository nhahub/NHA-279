package com.trailerly.network.dto

import com.squareup.moshi.Json

/**
 * Data Transfer Objects for parsing YouTube Data API v3 search responses.
 * These classes model the JSON structure returned by the YouTube search endpoint.
 */

/**
 * Represents the top-level response from the YouTube search endpoint.
 *
 * @property items A list of search results that match the query.
 */
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>
)

/**
 * Represents a single item in the YouTube search results.
 *
 * @property id The ID object that contains the video ID.
 * @property snippet The snippet object that contains display data like title and description.
 */
data class YouTubeSearchItem(
    val id: YouTubeVideoId,
    val snippet: YouTubeSnippet?
)

/**
 * Represents the nested 'id' object within a search result item, containing the video ID.
 *
 * @property videoId The unique identifier for the YouTube video. Null if the item is not a video.
 */
data class YouTubeVideoId(
    @Json(name = "videoId") val videoId: String?
)

/**
 * Represents the nested 'snippet' object, containing metadata about the video.
 *
 * @property title The title of the YouTube video.
 * @property description A brief description of the YouTube video.
 * @property channelId The ID of the channel that uploaded the video.
 * @property thumbnails Object containing thumbnail URLs for the video.
 */
data class YouTubeSnippet(
    val title: String?,
    val description: String?,
    @Json(name = "channelId") val channelId: String?,
    val thumbnails: YouTubeThumbnails?
)

/**
 * Represents the thumbnails object containing different sizes of video thumbnails.
 *
 * @property default The default thumbnail (120x90).
 * @property medium The medium thumbnail (320x180).
 * @property high The high thumbnail (480x360).
 */
data class YouTubeThumbnails(
    val default: YouTubeThumbnail?,
    val medium: YouTubeThumbnail?,
    val high: YouTubeThumbnail?
)

/**
 * Represents a single thumbnail with URL and dimensions.
 *
 * @property url The URL of the thumbnail image.
 * @property width The width of the thumbnail in pixels.
 * @property height The height of the thumbnail in pixels.
 */
data class YouTubeThumbnail(
    val url: String?,
    val width: Int?,
    val height: Int?
)

/**
 * Represents the top-level response from the YouTube Videos API endpoint.
 *
 * @property items A list of video details that match the requested video IDs.
 */
data class YouTubeVideoDetailsResponse(
    val items: List<YouTubeVideoDetailsItem>
)

/**
 * Represents a single item in the YouTube video details results.
 *
 * @property id The unique identifier for the YouTube video.
 * @property contentDetails The content details object containing duration and other metadata.
 * @property statistics The statistics object containing view count and other metrics.
 */
data class YouTubeVideoDetailsItem(
    val id: String,
    val contentDetails: YouTubeContentDetails?,
    val statistics: YouTubeStatistics?
)

/**
 * Represents the content details of a YouTube video.
 *
 * @property duration The duration of the video in ISO 8601 format (e.g., "PT4M13S").
 */
data class YouTubeContentDetails(
    val duration: String?
)

/**
 * Represents the statistics of a YouTube video.
 *
 * @property viewCount The number of times the video has been viewed.
 */
data class YouTubeStatistics(
    @Json(name = "viewCount") val viewCount: String?
)
