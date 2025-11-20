package com.example.jjutv

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class YouTubeWebViewPlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var onPlayerReady: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Fix for error 150/152
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true

            // Spoof User-Agent to bypass YouTube WebView detection
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("YouTubeWebView", "Page loaded: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("YouTubeWebView", "Error: $description")
                onError?.invoke(description ?: "Unknown error")
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                message?.let {
                    Log.d("YouTubeWebView", "Console [${it.messageLevel()}]: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }

        // Add JavaScript interface for communication
        addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onReady() {
                Log.d("YouTubeWebView", "Player ready")
                onPlayerReady?.invoke()
            }

            @JavascriptInterface
            fun onError(error: String) {
                Log.e("YouTubeWebView", "Player error: $error")
                onError?.invoke(error)
            }

            @JavascriptInterface
            fun log(message: String) {
                Log.d("YouTubeWebView", "JS: $message")
            }
        }, "Android")

        // Enable focus for TV remote
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun loadVideo(videoId: String) {
        Log.d("YouTubeWebView", "Loading video: $videoId")

        val html = """
            <!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
        * {
            margin: 0;
            padding: 0;
            overflow: hidden;
        }
        html, body {
            height: 100%;
            width: 100%;
            background-color: #000;
        }
        #player {
            width: 100%;
            height: 100%;
        }
    </style>
</head>
<body>
    <div id="player"></div>

    <script>
        // Load YouTube API (official domain)
        var tag = document.createElement('script');
        tag.src = "https://www.youtube.com/iframe_api";
        var firstScriptTag = document.getElementsByTagName('script')[0];
        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

        var player;

        function onYouTubeIframeAPIReady() {
            Android.log('YouTube API Ready');

            player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                videoId: '$videoId',
                host: "https://www.youtube.com",
                playerVars: {
                    'playsinline': 1,
                    'autoplay': 1,
                    'controls': 1,
                    'modestbranding': 1,
                    'rel': 0,
                    'showinfo': 0,
                    'iv_load_policy': 3,
                    'fs': 1,
                    'cc_load_policy': 0,
                    'disablekb': 0,
                    'enablejsapi': 1,
                    'origin': window.location.origin
                },
                events: {
                    'onReady': onPlayerReady,
                    'onStateChange': onPlayerStateChange,
                    'onError': onPlayerError
                }
            });
        }

        function onPlayerReady(event) {
            Android.log('Player ready, starting playback');
            Android.onReady();
            event.target.playVideo();
        }

        function onPlayerStateChange(event) {
            Android.log('Player state: ' + event.data);
        }

        function onPlayerError(event) {
            var errorMsg = 'Error code: ' + event.data;
            Android.log('Player error: ' + errorMsg);

            switch (event.data) {
                case 2:
                    errorMsg = 'Invalid video ID';
                    break;
                case 5:
                    errorMsg = 'HTML5 player error';
                    break;
                case 100:
                    errorMsg = 'Video not found or private';
                    break;
                case 101:
                case 150:
                case 153:
                    errorMsg = 'Video cannot be embedded (Uploader blocked it)';
                    break;
            }

            Android.onError(errorMsg);
        }

        // TV Remote Keyboard Controls
        document.addEventListener('keydown', function(e) {
            if (!player || !player.playVideo) return;

            switch(e.keyCode) {
                case 179: // Play/Pause
                case 32:
                    if (player.getPlayerState() === 1) {
                        player.pauseVideo();
                    } else {
                        player.playVideo();
                    }
                    break;
                case 37:
                    player.seekTo(player.getCurrentTime() - 10);
                    break;
                case 39:
                    player.seekTo(player.getCurrentTime() + 10);
                    break;
                case 38:
                    player.setVolume(Math.min(100, player.getVolume() + 10));
                    break;
                case 40:
                    player.setVolume(Math.max(0, player.getVolume() - 10));
                    break;
            }
        });
    </script>

</body>
</html>

        """.trimIndent()

        loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    }

    fun setOnPlayerReadyListener(listener: () -> Unit) {
        onPlayerReady = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }

    // Handle TV remote control keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // Play/Pause
                loadUrl("javascript:if(player && player.playVideo){if(player.getPlayerState()===1){player.pauseVideo()}else{player.playVideo()}}")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                loadUrl("javascript:if(player && player.playVideo){if(player.getPlayerState()===1){player.pauseVideo()}else{player.playVideo()}}")
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Rewind 10 seconds
                loadUrl("javascript:if(player && player.seekTo){player.seekTo(player.getCurrentTime()-10)}")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Forward 10 seconds
                loadUrl("javascript:if(player && player.seekTo){player.seekTo(player.getCurrentTime()+10)}")
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Volume up
                loadUrl("javascript:if(player && player.setVolume){var v=player.getVolume();player.setVolume(Math.min(100,v+10))}")
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Volume down
                loadUrl("javascript:if(player && player.setVolume){var v=player.getVolume();player.setVolume(Math.max(0,v-10))}")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun pauseVideo() {
        loadUrl("javascript:if(player && player.pauseVideo){player.pauseVideo()}")
    }

    fun playVideo() {
        loadUrl("javascript:if(player && player.playVideo){player.playVideo()}")
    }

    fun stopVideo() {
        loadUrl("javascript:if(player && player.stopVideo){player.stopVideo()}")
    }
}
