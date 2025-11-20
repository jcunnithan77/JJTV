package com.example.jjutv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BackendExtractor {

    // Backend server URL - Render deployment
    private val BACKEND_URL = "https://jjtv.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Extract video URL using backend server
     * This is the MOST RELIABLE method since it uses yt-dlp
     */
    suspend fun extractVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("BackendExtractor", "Extracting video via backend: $videoId")

            val apiUrl = "$BACKEND_URL/api/extract?video_id=$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)

                if (json.optBoolean("success", false)) {
                    val videoUrl = json.optString("url")
                    val title = json.optString("title", "Video")

                    if (videoUrl.isNotEmpty()) {
                        Log.d("BackendExtractor", "Backend extraction success: $title")
                        return@withContext videoUrl
                    }
                } else {
                    val error = json.optString("error", "Unknown error")
                    Log.w("BackendExtractor", "Backend returned error: $error")
                }
            } else {
                Log.w("BackendExtractor", "Backend request failed: ${response.code}")
            }

            return@withContext null

        } catch (e: Exception) {
            Log.e("BackendExtractor", "Backend extraction error: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Fetch Blippi channel videos from backend
     */
    suspend fun fetchBlippiVideos(maxResults: Int = 50): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("BackendExtractor", "Fetching Blippi videos from backend")

            val apiUrl = "$BACKEND_URL/api/channel/blippi?max_results=$maxResults"
            val request = Request.Builder()
                .url(apiUrl)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)

                if (json.optBoolean("success", false)) {
                    val videos = mutableListOf<VideoItem>()
                    val videosArray = json.optJSONArray("videos")

                    if (videosArray != null) {
                        for (i in 0 until videosArray.length()) {
                            val video = videosArray.getJSONObject(i)
                            videos.add(VideoItem(
                                id = video.optString("video_id"),
                                title = video.optString("title", "Blippi Video"),
                                thumbnail = video.optString("thumbnail", "")
                            ))
                        }
                    }

                    Log.d("BackendExtractor", "Fetched ${videos.size} Blippi videos from backend")
                    return@withContext videos
                }
            }

            Log.w("BackendExtractor", "Failed to fetch Blippi videos from backend")
            return@withContext emptyList()

        } catch (e: Exception) {
            Log.e("BackendExtractor", "Error fetching Blippi videos: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Extract playlist videos from backend
     */
    suspend fun extractPlaylist(playlistId: String, maxResults: Int = 50): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("BackendExtractor", "Fetching playlist from backend: $playlistId")

            val apiUrl = "$BACKEND_URL/api/playlist?playlist_id=$playlistId&max_results=$maxResults"
            val request = Request.Builder()
                .url(apiUrl)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)

                if (json.optBoolean("success", false)) {
                    val videos = mutableListOf<VideoItem>()
                    val videosArray = json.optJSONArray("videos")

                    if (videosArray != null) {
                        for (i in 0 until videosArray.length()) {
                            val video = videosArray.getJSONObject(i)
                            videos.add(VideoItem(
                                id = video.optString("video_id"),
                                title = video.optString("title", "Video"),
                                thumbnail = video.optString("thumbnail", "")
                            ))
                        }
                    }

                    Log.d("BackendExtractor", "Fetched ${videos.size} videos from playlist")
                    return@withContext videos
                }
            }

            return@withContext emptyList()

        } catch (e: Exception) {
            Log.e("BackendExtractor", "Error fetching playlist: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Test backend server connectivity
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("BackendExtractor", "Testing backend connection: $BACKEND_URL")

            val request = Request.Builder()
                .url(BACKEND_URL)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful

            if (success) {
                Log.d("BackendExtractor", "Backend server is online!")
            } else {
                Log.w("BackendExtractor", "Backend server returned: ${response.code}")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e("BackendExtractor", "Backend server is offline or unreachable: ${e.message}")
            return@withContext false
        }
    }
}
