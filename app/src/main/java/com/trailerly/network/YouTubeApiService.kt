package com.trailerly.network

import com.trailerly.network.dto.YouTubeSearchResponse
import com.trailerly.network.dto.YouTubeVideoDetailsResponse
import com.trailerly.util.TmdbConstants
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for interacting with the YouTube Data API v3.
 * This interface defines the API endpoints for searching YouTube.
 */
interface YouTubeApiService {

    /**
     * Searches for videos on YouTube based on a query.
     *
     * @param part Specifies the resource parts to be returned (e.g., "id,snippet").
     * @param query The search query string.
     * @param type Restricts the search to a specific type of resource (e.g., "video").
     * @param maxResults The maximum number of results to return.
     * @param apiKey Your YouTube Data API key.
     * @param channelId Optional channel ID to restrict search to a specific channel.
     * @return A [YouTubeSearchResponse] object containing the search results.
     */
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = TmdbConstants.YOUTUBE_SEARCH_PART,
        @Query("q") query: String,
        @Query("type") type: String = TmdbConstants.YOUTUBE_SEARCH_TYPE,
        @Query("maxResults") maxResults: Int = TmdbConstants.YOUTUBE_SEARCH_MAX_RESULTS,
        @Query("key") apiKey: String,
        @Query("videoEmbeddable") videoEmbeddable: String = TmdbConstants.YOUTUBE_SEARCH_EMBEDDABLE,
        @Query("channelId") channelId: String? = null
    ): YouTubeSearchResponse

    /**
     * Retrieves details for one or more YouTube videos.
     *
     * @param part Specifies the resource parts to be returned (e.g., "contentDetails,statistics").
     * @param id Comma-separated list of video IDs.
     * @param apiKey Your YouTube Data API key.
     * @return A [YouTubeVideoDetailsResponse] object containing the video details.
     */
    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "contentDetails,statistics",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): YouTubeVideoDetailsResponse
}