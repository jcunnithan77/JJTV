package com.example.jjutv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NewPipeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    init {
        // Initialize NewPipe with custom downloader
        try {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val okHttpRequest = okhttp3.Request.Builder()
                        .url(request.url())
                        .apply {
                            // Add NewPipe's headers first
                            request.headers().forEach { (key, values) ->
                                values.forEach { value ->
                                    addHeader(key, value)
                                }
                            }

                            // Override/Add browser-like headers to bypass YouTube detection
                            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            header("Accept-Language", "en-US,en;q=0.9")
                            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                            header("Referer", "https://www.youtube.com/")
                            header("Origin", "https://www.youtube.com")
                            header("DNT", "1")
                            header("Connection", "keep-alive")
                            header("Upgrade-Insecure-Requests", "1")
                            header("Sec-Fetch-Dest", "document")
                            header("Sec-Fetch-Mode", "navigate")
                            header("Sec-Fetch-Site", "none")
                            header("Sec-Fetch-User", "?1")
                            header("Cache-Control", "max-age=0")
                        }
                        .build()

                    val response = client.newCall(okHttpRequest).execute()
                    val responseBody = response.body?.string() ?: ""

                    Log.d("NewPipeExtractor", "Request to ${request.url()} returned ${response.code}")

                    return Response(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        responseBody,
                        response.request.url.toString()
                    )
                }
            })
            Log.d("NewPipeExtractor", "NewPipe initialized successfully")
        } catch (e: Exception) {
            Log.e("NewPipeExtractor", "Failed to initialize NewPipe", e)
        }
    }

    /**
     * Extract video URL using NewPipe
     * This bypasses YouTube's embedding restrictions
     */
    suspend fun extractVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("NewPipeExtractor", "Extracting video with NewPipe: $videoId")

            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)

            // Try to get HLS stream first (best for streaming)
            val hlsUrl = streamInfo.hlsUrl
            if (!hlsUrl.isNullOrEmpty()) {
                Log.d("NewPipeExtractor", "Found HLS stream via NewPipe")
                return@withContext hlsUrl
            }

            // Get best quality video stream
            val videoStreams = streamInfo.videoStreams
            if (videoStreams.isNotEmpty()) {
                // Sort by quality and get best
                val bestStream = videoStreams
                    .filter { it.isVideoOnly == false } // Get streams with audio
                    .maxByOrNull { it.getResolution().toIntOrNull() ?: 0 }
                    ?: videoStreams.firstOrNull() // Fallback to any stream

                if (bestStream != null) {
                    Log.d("NewPipeExtractor", "Found video stream: ${bestStream.getResolution()}")
                    return@withContext bestStream.url
                }
            }

            // Try video-only streams as last resort
            val videoOnlyStreams = streamInfo.videoOnlyStreams
            if (videoOnlyStreams.isNotEmpty()) {
                val bestVideoOnly = videoOnlyStreams.maxByOrNull {
                    it.getResolution().toIntOrNull() ?: 0
                }
                if (bestVideoOnly != null) {
                    Log.d("NewPipeExtractor", "Found video-only stream: ${bestVideoOnly.getResolution()}")
                    return@withContext bestVideoOnly.url
                }
            }

            Log.w("NewPipeExtractor", "No streams found for video: $videoId")
            return@withContext null

        } catch (e: Exception) {
            Log.e("NewPipeExtractor", "Error extracting with NewPipe: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Extract playlist videos using NewPipe
     */
    suspend fun extractPlaylist(playlistId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("NewPipeExtractor", "Extracting playlist with NewPipe: $playlistId")

            val url = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(
                ServiceList.YouTube,
                url
            )

            val videoIds = playlistInfo.relatedItems
                .mapNotNull { item ->
                    try {
                        // Extract video ID from URL
                        val videoUrl = item.url
                        val videoIdMatch = Regex("(?:v=|/)([a-zA-Z0-9_-]{11})").find(videoUrl)
                        videoIdMatch?.groupValues?.get(1)
                    } catch (e: Exception) {
                        null
                    }
                }

            Log.d("NewPipeExtractor", "Extracted ${videoIds.size} videos from playlist")
            return@withContext videoIds

        } catch (e: Exception) {
            Log.e("NewPipeExtractor", "Error extracting playlist: ${e.message}", e)
            return@withContext emptyList()
        }
    }
}
