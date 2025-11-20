package com.example.jjutv

import android.content.Intent
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var standbyText: TextView
    private lateinit var recyclerView: RecyclerView
    private val groupList = mutableListOf<VideoGroup>()
    private lateinit var channelManager: ChannelManager
    private val playlistExtractor = YouTubePlaylistExtractor()

//    private val standbyHandler = Handler(Looper.getMainLooper())
//    private val standbyRunnable = object : Runnable {
//        override fun run() {
//            checkStandbyTime()
//            standbyHandler.postDelayed(this, 1 * 60 * 1000) // every 5 minutes
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelManager = ChannelManager(this)
        standbyText = findViewById(R.id.standbyText)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.addItemDecoration(
            object : RecyclerView.ItemDecoration() {
                private val spacing = 10 // in pixels, adjust as needed

                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(spacing, spacing, spacing, spacing)
                }
            }
        )

        val passedCaption = intent.getStringExtra("standby_caption")
        if (passedCaption != null) {
            standbyText.text = passedCaption
            standbyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
           // return
        }
        val refreshButton: ImageButton = findViewById(R.id.refreshButton)
        refreshButton.setOnClickListener {
            groupList.clear()
            recyclerView.adapter?.notifyDataSetChanged()
            fetchData()
        }

        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        fetchData()
       // standbyHandler.post(standbyRunnable)
    }

    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load from local videos.json file
                val jsonString = assets.open("videos.json").bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                val groupsArray = json.optJSONArray("groups")
                if (groupsArray != null) {
                    for (i in 0 until groupsArray.length()) {
                        val group = groupsArray.getJSONObject(i)
                        val groupName = group.getString("name")
                        val groupThumb = group.getString("thumbnail")

                        val videos = mutableListOf<VideoItem>()

                        // Check if this group has a playlist URL
                        val playlistUrl = group.optString("playlistUrl")
                        if (playlistUrl.isNotEmpty()) {
                            // Extract videos from playlist
                            Log.d("FetchData", "Extracting playlist for group: $groupName")
                            withContext(Dispatchers.Main) {
                                standbyText.text = "Loading playlist: $groupName..."
                                standbyText.visibility = View.VISIBLE
                            }

                            val videoIds = playlistExtractor.extractPlaylistVideos(playlistUrl)
                            Log.d("FetchData", "Extracted ${videoIds.size} videos from playlist")

                            for (videoId in videoIds) {
                                val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                videos.add(VideoItem(videoId, thumbnail, ""))
                            }

                            if (videoIds.isEmpty()) {
                                Log.w("FetchData", "No videos extracted from playlist: $playlistUrl")
                            }
                        } else {
                            // Use individual video IDs from array
                            val videosArray = group.optJSONArray("videos")
                            if (videosArray != null) {
                                for (j in 0 until videosArray.length()) {
                                    val videoId = videosArray.getString(j)
                                    val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                    videos.add(VideoItem(videoId, thumbnail, ""))
                                }
                            }
                        }

                        // Only add group if it has videos
                        if (videos.isNotEmpty()) {
                            groupList.add(VideoGroup(groupName, groupThumb, videos))
                        } else {
                            Log.w("FetchData", "Skipping empty group: $groupName")
                        }
                    }
                }

                Log.d("FetchData", "Loaded ${groupList.size} groups from local JSON")
                withContext(Dispatchers.Main) {
                    if (groupList.isNotEmpty()) {
                        recyclerView.adapter = GroupAdapter(groupList, this@MainActivity)
                        standbyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    } else {
                        standbyText.text = "No videos available in videos.json"
                        standbyText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("FetchData", "Error loading videos.json: ${e.message}")
                withContext(Dispatchers.Main) {
                    standbyText.text = "Error loading videos: ${e.message}"
                    standbyText.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from settings
        groupList.clear()
        recyclerView.adapter?.notifyDataSetChanged()
        fetchData()
    }

    private fun checkStandbyTime() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val standbyData = JSONArray(
                    URL("https://raw.githubusercontent.com/jcunnithan77/tv/refs/heads/main/StandBy").readText()
                )

                val calendar = Calendar.getInstance()
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(Calendar.MINUTE)
                val currentTotalMinutes = currentHour * 60 + currentMinute

                for (i in 0 until standbyData.length()) {
                    val item = standbyData.getJSONObject(i)
                    val stime = item.getString("stime") // e.g., "10:10"
                    val etime = item.getString("etime") // e.g., "10:20"

                    val startParts = stime.split(":").map { it.toInt() }
                    val endParts = etime.split(":").map { it.toInt() }

                    val startMinutes = startParts[0] * 60 + startParts[1]
                    val endMinutes = endParts[0] * 60 + endParts[1]

                    if (currentTotalMinutes in startMinutes until endMinutes) {
                        withContext(Dispatchers.Main) {
                            closeAllActivitiesAndShowStandby(item.getString("Caption"))
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun closeAllActivitiesAndShowStandby(caption: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("standby_caption", caption)
        startActivity(intent)
        finish()
    }
}
