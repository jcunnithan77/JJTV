// PlayerActivity.kt
package com.example.jjutv

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingView: ProgressBar
    private lateinit var errorText: TextView
    private var player: ExoPlayer? = null

    // Multiple extractors for fallback
    private val backendExtractor = BackendExtractor()  // BEST - uses yt-dlp on server
    private val youtubeExtractor = YouTubeExtractor()
    private val newPipeExtractor = NewPipeExtractor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loadingView = findViewById(R.id.loading_progress)
        errorText = findViewById(R.id.error_text)

        val videoId = intent.getStringExtra("videoId") ?: run {
            Toast.makeText(this, "No video ID provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("PlayerActivity", "Loading video: $videoId")
        loadingView.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        // Extract and play video
        extractAndPlay(videoId)
    }

    private fun extractAndPlay(videoId: String) {
        lifecycleScope.launch {
            try {
                Log.d("PlayerActivity", "Extracting stream for: $videoId")

                var streamUrl: String? = null

                // Try Method 1: Backend Server with yt-dlp (MOST RELIABLE!)
                Log.d("PlayerActivity", "Method 1: Backend server with yt-dlp...")
                streamUrl = backendExtractor.extractVideoUrl(videoId)

                // Try Method 2: Piped API
                if (streamUrl == null) {
                    Log.d("PlayerActivity", "Method 2: Piped API...")
                    streamUrl = youtubeExtractor.extractVideoUrlViaPiped(videoId)
                }

                // Try Method 3: Invidious API
                if (streamUrl == null) {
                    Log.d("PlayerActivity", "Method 3: Invidious API...")
                    streamUrl = youtubeExtractor.extractVideoUrlViaInvidious(videoId)
                }

                // Try Method 4: Direct YouTube scraping
                if (streamUrl == null) {
                    Log.d("PlayerActivity", "Method 4: YouTube scraping...")
                    streamUrl = youtubeExtractor.extractVideoUrl(videoId)
                }

                // Try Method 5: NewPipe (last resort)
                if (streamUrl == null) {
                    Log.d("PlayerActivity", "Method 5: NewPipe extractor...")
                    streamUrl = newPipeExtractor.extractVideoUrl(videoId)
                }

                runOnUiThread {
                    if (streamUrl != null) {
                        Log.d("PlayerActivity", "Stream URL found! Initializing player")
                        initializePlayer(streamUrl)
                    } else {
                        showError("""
                            Unable to play this video.

                            All extraction methods failed.

                            The video may be:
                            • Unavailable or deleted
                            • Region-locked
                            • Age-restricted

                            Please try another video.
                        """.trimIndent())
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Error loading video:\n${e.message}")
                    Log.e("PlayerActivity", "Extraction failed", e)
                }
            }
        }
    }

    private fun initializePlayer(streamUrl: String) {
        try {
            Log.d("PlayerActivity", "Initializing ExoPlayer with URL: ${streamUrl.take(100)}...")

            // Initialize ExoPlayer
            player = ExoPlayer.Builder(this).build().also { exoPlayer ->
                playerView.player = exoPlayer
                playerView.visibility = View.VISIBLE

                // Set up media item
                val mediaItem = MediaItem.fromUri(streamUrl)
                exoPlayer.setMediaItem(mediaItem)

                // Add listener
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                loadingView.visibility = View.GONE
                                Log.d("PlayerActivity", "Player ready, starting playback")
                                Toast.makeText(this@PlayerActivity, "Video loaded", Toast.LENGTH_SHORT).show()
                            }
                            Player.STATE_BUFFERING -> {
                                loadingView.visibility = View.VISIBLE
                                Log.d("PlayerActivity", "Buffering...")
                            }
                            Player.STATE_ENDED -> {
                                Log.d("PlayerActivity", "Playback ended")
                                finish()
                            }
                            Player.STATE_IDLE -> {
                                Log.d("PlayerActivity", "Player idle")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PlayerActivity", "Player error: ${error.message}")
                        showError("Playback error:\n${error.message}\n\nThe stream URL may have expired.\nPress back to try again.")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("PlayerActivity", "Is playing: $isPlaying")
                    }
                })

                // Prepare and play
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                // Focus for TV remote
                playerView.requestFocus()
            }
        } catch (e: Exception) {
            showError("Failed to initialize player:\n${e.message}")
            Log.e("PlayerActivity", "Player initialization failed", e)
        }
    }

    private fun showError(message: String) {
        loadingView.visibility = View.GONE
        playerView.visibility = View.GONE
        errorText.text = message
        errorText.visibility = View.VISIBLE
        Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    override fun onBackPressed() {
        super.onBackPressed()
        player?.release()
        finish()
    }
}
