package com.example.jjutv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun extractVideoUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("YouTubeExtractor", "Extracting URL via YouTube embed for video: $videoId")

            // Try multiple YouTube page endpoints
            val urls = listOf(
                "https://www.youtube.com/watch?v=$videoId",
                "https://www.youtube.com/embed/$videoId",
                "https://m.youtube.com/watch?v=$videoId"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    val response = client.newCall(request).execute()
                    try {
                        if (!response.isSuccessful) continue

                        val html = response.body?.string()
                        if (html == null) continue

                        // Try multiple patterns to find player data
                        val patterns = listOf(
                        "var ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});",
                        "ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});",
                        "\"streamingData\":(\\{.+?\\}),\"playbackTracking\""
                    )

                    for (patternStr in patterns) {
                        try {
                            val pattern = Pattern.compile(patternStr, Pattern.DOTALL)
                            val matcher = pattern.matcher(html)

                            if (matcher.find()) {
                                val jsonStr = matcher.group(1) ?: continue
                                val jsonObject = try {
                                    JSONObject(jsonStr)
                                } catch (e: Exception) {
                                    Log.w("YouTubeExtractor", "Failed to parse JSON from pattern: $patternStr")
                                    continue
                                }

                                // Try to extract streaming data
                                val streamingData = jsonObject.optJSONObject("streamingData")
                                if (streamingData != null) {
                                    // Try HLS first
                                    val hlsUrl = streamingData.optString("hlsManifestUrl")
                                    if (hlsUrl.isNotEmpty() && hlsUrl != "null") {
                                        Log.d("YouTubeExtractor", "Found HLS URL via YouTube")
                                        return@withContext hlsUrl
                                    }

                                    // Try adaptive formats
                                    val formats = streamingData.optJSONArray("adaptiveFormats")
                                        ?: streamingData.optJSONArray("formats")

                                    if (formats != null && formats.length() > 0) {
                                        for (i in 0 until formats.length()) {
                                            val format = formats.getJSONObject(i)
                                            val url = format.optString("url")
                                            if (url.isNotEmpty() && url != "null") {
                                                Log.d("YouTubeExtractor", "Found stream URL via YouTube")
                                                return@withContext url
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("YouTubeExtractor", "Pattern $patternStr failed", e)
                            continue
                        }
                    }
                    } finally {
                        response.close()
                    }
                } catch (e: Exception) {
                    Log.w("YouTubeExtractor", "Failed to fetch from $url", e)
                    continue
                }
            }

            Log.w("YouTubeExtractor", "YouTube embed extraction failed - will try other methods")
            return@withContext null

        } catch (e: Exception) {
            Log.e("YouTubeExtractor", "Error in YouTube extraction", e)
            return@withContext null
        }
    }

    // Method 2: Use Piped API (more reliable than Invidious)
    suspend fun extractVideoUrlViaPiped(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val pipedInstances = listOf(
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.tokhmi.xyz",
                "https://pipedapi.moomoo.me",
                "https://piped-api.privacy.com.de"
            )

            for (instance in pipedInstances) {
                try {
                    val apiUrl = "$instance/streams/$videoId"
                    Log.d("YouTubeExtractor", "Trying Piped instance: $instance")

                    val request = Request.Builder()
                        .url(apiUrl)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build()

                    val response = client.newCall(request).execute()
                    try {
                        if (!response.isSuccessful) continue

                        val responseBody = response.body?.string()
                        if (responseBody == null) continue

                        // Check if response is HTML (error page)
                        if (responseBody.trim().startsWith("<")) {
                            Log.w("YouTubeExtractor", "Piped returned HTML error page")
                            continue
                        }

                        val json = try {
                            JSONObject(responseBody)
                        } catch (e: Exception) {
                            Log.w("YouTubeExtractor", "Invalid JSON from Piped: ${e.message}")
                            continue
                        }

                        // Try HLS URL first (best for streaming)
                        val hlsUrl = json.optString("hls")
                        if (hlsUrl.isNotEmpty() && hlsUrl != "null") {
                            Log.d("YouTubeExtractor", "Found HLS URL via Piped: $hlsUrl")
                            return@withContext hlsUrl
                        }

                        // Try video streams
                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null && videoStreams.length() > 0) {
                            // Get best quality stream
                            var bestUrl: String? = null
                            var bestQuality = 0

                            for (i in 0 until videoStreams.length()) {
                                val stream = videoStreams.getJSONObject(i)
                                val url = stream.optString("url")
                                val quality = stream.optString("quality", "0p")
                                val qualityNum = quality.replace("p", "").toIntOrNull() ?: 0

                                if (url.isNotEmpty() && qualityNum > bestQuality) {
                                    bestUrl = url
                                    bestQuality = qualityNum
                                }
                            }

                            if (bestUrl != null) {
                                Log.d("YouTubeExtractor", "Found video stream via Piped: $bestUrl (${bestQuality}p)")
                                return@withContext bestUrl
                            }
                        }
                    } finally {
                        response.close()
                    }
                } catch (e: Exception) {
                    Log.w("YouTubeExtractor", "Failed with Piped instance $instance", e)
                    continue
                }
            }

            Log.w("YouTubeExtractor", "All Piped instances failed")
            return@withContext null
        } catch (e: Exception) {
            Log.e("YouTubeExtractor", "Error with Piped extraction", e)
            return@withContext null
        }
    }

    // Method 3: Fallback using Invidious API
    suspend fun extractVideoUrlViaInvidious(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val invidiousInstances = listOf(
                "https://inv.riverside.rocks",
                "https://invidious.snopyta.org",
                "https://vid.puffyan.us",
                "https://invidious.projectsegfau.lt"
            )

            for (instance in invidiousInstances) {
                try {
                    val apiUrl = "$instance/api/v1/videos/$videoId"
                    val request = Request.Builder()
                        .url(apiUrl)
                        .build()

                    val response = client.newCall(request).execute()
                    try {
                        if (!response.isSuccessful) continue

                        val responseBody = response.body?.string()
                        if (responseBody == null) continue

                        // Check if response is HTML (error page)
                        if (responseBody.trim().startsWith("<")) {
                            Log.w("YouTubeExtractor", "Invidious returned HTML error page")
                            continue
                        }

                        val json = try {
                            JSONObject(responseBody)
                        } catch (e: Exception) {
                            Log.w("YouTubeExtractor", "Invalid JSON from Invidious: ${e.message}")
                            continue
                        }

                        // Try HLS first
                        val hlsUrl = json.optString("hlsUrl")
                        if (hlsUrl.isNotEmpty() && hlsUrl != "null") {
                            Log.d("YouTubeExtractor", "Found HLS URL via Invidious: $hlsUrl")
                            return@withContext hlsUrl
                        }

                        // Try adaptive formats
                        val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                        if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                            for (i in 0 until adaptiveFormats.length()) {
                                val format = adaptiveFormats.getJSONObject(i)
                                val type = format.optString("type", "")
                                if (type.contains("video/mp4")) {
                                    val url = format.getString("url")
                                    Log.d("YouTubeExtractor", "Found URL via Invidious: $url")
                                    return@withContext url
                                }
                            }
                        }
                    } finally {
                        response.close()
                    }
                } catch (e: Exception) {
                    Log.w("YouTubeExtractor", "Failed with Invidious instance $instance", e)
                    continue
                }
            }

            Log.w("YouTubeExtractor", "All Invidious instances failed")
            return@withContext null
        } catch (e: Exception) {
            Log.e("YouTubeExtractor", "Error with Invidious extraction", e)
            return@withContext null
        }
    }
}
