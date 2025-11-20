package com.example.jjutv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubePlaylistExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Extract video IDs from a YouTube playlist URL
     * Supports formats:
     * - https://www.youtube.com/playlist?list=PLxxxxxx
     * - https://youtube.com/playlist?list=PLxxxxxx
     */
    suspend fun extractPlaylistVideos(playlistUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val playlistId = extractPlaylistId(playlistUrl)
            if (playlistId == null) {
                Log.e("PlaylistExtractor", "Invalid playlist URL: $playlistUrl")
                return@withContext emptyList()
            }

            Log.d("PlaylistExtractor", "Extracting playlist: $playlistId")

            // Try multiple methods in order of reliability
            var videos = tryPipedAPI(playlistId)
            if (videos.isEmpty()) {
                Log.d("PlaylistExtractor", "Piped API failed, trying Invidious...")
                videos = tryInvidiousAPI(playlistId) ?: emptyList()
            }
            if (videos.isEmpty()) {
                Log.d("PlaylistExtractor", "Invidious failed, trying web scraping...")
                videos = tryYouTubeWebScraping(playlistId) ?: emptyList()
            }

            Log.d("PlaylistExtractor", "Extracted ${videos.size} videos from playlist $playlistId")
            return@withContext videos

        } catch (e: Exception) {
            Log.e("PlaylistExtractor", "Error extracting playlist", e)
            return@withContext emptyList()
        }
    }

    private suspend fun tryPipedAPI(playlistId: String): List<String> = withContext(Dispatchers.IO) {
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me",
            "https://piped-api.privacy.com.de"
        )

        for (instance in pipedInstances) {
            try {
                val apiUrl = "$instance/playlists/$playlistId"
                Log.d("PlaylistExtractor", "Trying Piped instance: $instance")

                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: continue)
                    val relatedStreams = json.optJSONArray("relatedStreams")

                    if (relatedStreams != null && relatedStreams.length() > 0) {
                        val videoIds = mutableListOf<String>()
                        for (i in 0 until relatedStreams.length()) {
                            val stream = relatedStreams.getJSONObject(i)
                            val url = stream.optString("url", "")
                            // Extract video ID from URL like "/watch?v=dQw4w9WgXcQ"
                            val videoIdMatch = Regex("/watch\\?v=([a-zA-Z0-9_-]{11})").find(url)
                            if (videoIdMatch != null) {
                                videoIds.add(videoIdMatch.groupValues[1])
                            }
                        }

                        if (videoIds.isNotEmpty()) {
                            Log.d("PlaylistExtractor", "Extracted ${videoIds.size} videos via Piped")
                            return@withContext videoIds
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaylistExtractor", "Failed with Piped instance $instance", e)
                continue
            }
        }

        return@withContext emptyList()
    }

    private fun extractPlaylistId(url: String): String? {
        // Extract playlist ID from URL
        val patterns = listOf(
            Pattern.compile("list=([a-zA-Z0-9_-]+)"),
            Pattern.compile("p/([a-zA-Z0-9_-]+)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }

        // If URL is just the playlist ID
        if (url.matches(Regex("[a-zA-Z0-9_-]+"))) {
            return url
        }

        return null
    }

    private suspend fun tryInvidiousAPI(playlistId: String): List<String>? = withContext(Dispatchers.IO) {
        val invidiousInstances = listOf(
            "https://invidious.snopyta.org",
            "https://invidious.kavin.rocks",
            "https://vid.puffyan.us",
            "https://invidious.projectsegfau.lt"
        )

        for (instance in invidiousInstances) {
            try {
                val apiUrl = "$instance/api/v1/playlists/$playlistId"
                Log.d("PlaylistExtractor", "Trying Invidious instance: $instance")

                val request = Request.Builder()
                    .url(apiUrl)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: continue)
                    val videos = json.optJSONArray("videos")

                    if (videos != null && videos.length() > 0) {
                        val videoIds = mutableListOf<String>()
                        for (i in 0 until videos.length()) {
                            val video = videos.getJSONObject(i)
                            val videoId = video.optString("videoId")
                            if (videoId.isNotEmpty()) {
                                videoIds.add(videoId)
                            }
                        }

                        if (videoIds.isNotEmpty()) {
                            Log.d("PlaylistExtractor", "Successfully extracted ${videoIds.size} videos via Invidious")
                            return@withContext videoIds
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaylistExtractor", "Failed with Invidious instance $instance", e)
                continue
            }
        }

        return@withContext null
    }

    private suspend fun tryYouTubeWebScraping(playlistId: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            Log.d("PlaylistExtractor", "Trying web scraping for: $playlistUrl")

            val request = Request.Builder()
                .url(playlistUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            // Try to extract ytInitialData
            val pattern = Pattern.compile("var ytInitialData = (\\{.+?\\});", Pattern.DOTALL)
            val matcher = pattern.matcher(html)

            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: return@withContext null
                val videoIds = mutableListOf<String>()

                // Find all video IDs in the JSON
                val videoIdPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                val videoMatcher = videoIdPattern.matcher(jsonStr)

                while (videoMatcher.find()) {
                    val videoId = videoMatcher.group(1)
                    if (videoId != null && !videoIds.contains(videoId)) {
                        videoIds.add(videoId)
                    }
                }

                if (videoIds.isNotEmpty()) {
                    Log.d("PlaylistExtractor", "Extracted ${videoIds.size} videos via web scraping")
                    return@withContext videoIds
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e("PlaylistExtractor", "Error in web scraping", e)
            return@withContext null
        }
    }

    /**
     * Check if a string is a YouTube playlist URL
     */
    fun isPlaylistUrl(url: String): Boolean {
        return url.contains("list=") || url.contains("/playlist")
    }
}
