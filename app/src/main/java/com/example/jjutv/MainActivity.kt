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
    private val backendExtractor = BackendExtractor()

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
            fetchData()
        }

        fetchData()
       // standbyHandler.post(standbyRunnable)
    }

    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear existing groups to prevent duplicates
                withContext(Dispatchers.Main) {
                    groupList.clear()
                    recyclerView.adapter?.notifyDataSetChanged()
                }

                // Fetch video groups from backend server
                withContext(Dispatchers.Main) {
                    standbyText.text = "Loading video groups from backend..."
                    standbyText.visibility = View.VISIBLE
                }

                Log.d("FetchData", "Fetching groups from backend")
                val backendGroups = backendExtractor.fetchGroups()

                if (backendGroups.isNotEmpty()) {
                    // Backend fetch successful
                    groupList.addAll(backendGroups)
                    Log.d("FetchData", "Successfully loaded ${backendGroups.size} groups from backend")

                    withContext(Dispatchers.Main) {
                        recyclerView.adapter = GroupAdapter(groupList, this@MainActivity)
                        standbyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    // No groups available
                    Log.w("FetchData", "No groups available from backend")
                    withContext(Dispatchers.Main) {
                        standbyText.text = "No video groups available.\nCreate groups via Admin Panel at:\nhttp://192.168.1.5:5000/admin"
                        standbyText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("FetchData", "Error loading videos from backend: ${e.message}")
                withContext(Dispatchers.Main) {
                    standbyText.text = "Error: Cannot connect to backend server.\nMake sure backend is running in Termux:\npython ~/jjtv-backend/server.py"
                    standbyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Don't auto-refresh to prevent duplicate loading
        // User can use refresh button if needed
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
