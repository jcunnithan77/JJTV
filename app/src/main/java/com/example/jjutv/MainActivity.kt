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
    private lateinit var standbyContainer: View
    private lateinit var recyclerView: RecyclerView
    private val groupList = mutableListOf<VideoGroup>()
    private lateinit var channelManager: ChannelManager
    private val backendExtractor = BackendExtractor()
    private var isInStandbyMode = false

    private val standbyHandler = Handler(Looper.getMainLooper())
    private val standbyRunnable = object : Runnable {
        override fun run() {
            checkStandbyTime()
            standbyHandler.postDelayed(this, 1 * 60 * 1000) // every 1 minute
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelManager = ChannelManager(this)
        standbyText = findViewById(R.id.standbyText)
        standbyContainer = findViewById(R.id.standbyContainer)
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
            isInStandbyMode = true
            standbyText.text = passedCaption
            standbyContainer.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            // Hide buttons in standby mode
            findViewById<ImageButton>(R.id.refreshButton).visibility = View.GONE
            return
        } else {
            isInStandbyMode = false
        }
        val refreshButton: ImageButton = findViewById(R.id.refreshButton)
        refreshButton.setOnClickListener {
            fetchData()
        }

        fetchData()
        standbyHandler.post(standbyRunnable)
    }

    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear existing groups to prevent duplicates
                withContext(Dispatchers.Main) {
                    groupList.clear()
                    recyclerView.adapter?.notifyDataSetChanged()
                }

                // Don't show loading message if in standby mode
                if (!isInStandbyMode) {
                    // Fetch video groups from backend server
                    withContext(Dispatchers.Main) {
                        standbyText.text = "Loading video groups from backend..."
                        standbyContainer.visibility = View.VISIBLE
                    }
                }

                Log.d("FetchData", "Fetching groups from backend")
                val backendGroups = backendExtractor.fetchGroups()

                if (backendGroups.isNotEmpty()) {
                    // Backend fetch successful
                    groupList.addAll(backendGroups)
                    Log.d("FetchData", "Successfully loaded ${backendGroups.size} groups from backend")

                    withContext(Dispatchers.Main) {
                        if (!isInStandbyMode) {
                            recyclerView.adapter = GroupAdapter(groupList, this@MainActivity)
                            standbyContainer.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                    }
                } else {
                    // No groups available
                    Log.w("FetchData", "No groups available from backend")
                    withContext(Dispatchers.Main) {
                        if (!isInStandbyMode) {
                            standbyText.text = "No video groups available.\nCreate groups via Admin Panel at:\nhttp://127.0.0.1:5000/admin"
                            standbyContainer.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("FetchData", "Error loading videos from backend: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (!isInStandbyMode) {
                        standbyText.text = "Error: Cannot connect to backend server.\nMake sure backend is running in Termux:\npython ~/jjtv-backend/server.py"
                        standbyContainer.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
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
                // Fetch schedules from backend
                val schedulesJson = backendExtractor.fetchSchedules()
                if (schedulesJson == null) return@launch

                val schedulesArray = schedulesJson.getJSONArray("schedules")

                val calendar = Calendar.getInstance()
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(Calendar.MINUTE)
                val currentTotalMinutes = currentHour * 60 + currentMinute

                // Get current day of week
                val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "monday"
                    Calendar.TUESDAY -> "tuesday"
                    Calendar.WEDNESDAY -> "wednesday"
                    Calendar.THURSDAY -> "thursday"
                    Calendar.FRIDAY -> "friday"
                    Calendar.SATURDAY -> "saturday"
                    Calendar.SUNDAY -> "sunday"
                    else -> ""
                }

                var inSchedule = false

                for (i in 0 until schedulesArray.length()) {
                    val schedule = schedulesArray.getJSONObject(i)
                    val startTime = schedule.getString("start_time") // e.g., "14:00"
                    val endTime = schedule.getString("end_time") // e.g., "16:00"
                    val message = schedule.getString("message")
                    val daysArray = schedule.getJSONArray("days")

                    // Check if today is in the schedule days
                    var isToday = false
                    for (j in 0 until daysArray.length()) {
                        if (daysArray.getString(j) == dayOfWeek) {
                            isToday = true
                            break
                        }
                    }

                    if (!isToday) continue

                    val startParts = startTime.split(":").map { it.toInt() }
                    val endParts = endTime.split(":").map { it.toInt() }

                    val startMinutes = startParts[0] * 60 + startParts[1]
                    val endMinutes = endParts[0] * 60 + endParts[1]

                    if (currentTotalMinutes in startMinutes until endMinutes) {
                        inSchedule = true
                        // Only trigger standby if not already in standby mode
                        if (!isInStandbyMode) {
                            withContext(Dispatchers.Main) {
                                showStandbyScreen(message)
                            }
                        }
                        return@launch
                    }
                }

                // If no schedule is active and we're in standby mode, exit standby
                if (!inSchedule && isInStandbyMode) {
                    withContext(Dispatchers.Main) {
                        exitStandbyMode()
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking schedules: ${e.message}")
                e.printStackTrace()
            }
        }
    }


    private fun showStandbyScreen(message: String) {
        isInStandbyMode = true
        standbyText.text = message
        standbyContainer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        findViewById<ImageButton>(R.id.refreshButton).visibility = View.GONE
    }

    private fun exitStandbyMode() {
        isInStandbyMode = false
        standbyContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.refreshButton).visibility = View.VISIBLE
        // Reload videos
        fetchData()
    }

    private fun closeAllActivitiesAndShowStandby(caption: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("standby_caption", caption)
        startActivity(intent)
        finish()
    }
}
