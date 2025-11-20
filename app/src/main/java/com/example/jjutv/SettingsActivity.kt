package com.example.jjutv

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var channelManager: ChannelManager
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var channelNameInput: EditText
    private lateinit var playlistUrlInput: EditText
    private lateinit var thumbnailUrlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        channelManager = ChannelManager(this)

        channelNameInput = findViewById(R.id.channelNameInput)
        playlistUrlInput = findViewById(R.id.playlistUrlInput)
        thumbnailUrlInput = findViewById(R.id.thumbnailUrlInput)

        val addChannelButton: Button = findViewById(R.id.addChannelButton)
        val recyclerView: RecyclerView = findViewById(R.id.channelsRecyclerView)

        // Setup RecyclerView
        channelAdapter = ChannelAdapter(mutableListOf()) { channel ->
            showRemoveConfirmation(channel)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = channelAdapter

        // Load channels
        loadChannels()

        // Add channel button
        addChannelButton.setOnClickListener {
            addChannel()
        }
    }

    private fun loadChannels() {
        val channels = channelManager.getChannels()
        channelAdapter.updateChannels(channels)
    }

    private fun addChannel() {
        val name = channelNameInput.text.toString().trim()
        val playlistUrl = playlistUrlInput.text.toString().trim()
        val thumbnail = thumbnailUrlInput.text.toString().trim()
            .ifEmpty { "https://via.placeholder.com/300x200" }

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter channel name", Toast.LENGTH_SHORT).show()
            return
        }

        if (playlistUrl.isEmpty()) {
            Toast.makeText(this, "Please enter playlist URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (!playlistUrl.startsWith("http://") && !playlistUrl.startsWith("https://")) {
            Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            return
        }

        val channel = Channel(
            id = UUID.randomUUID().toString(),
            name = name,
            thumbnail = thumbnail,
            playlistUrl = playlistUrl
        )

        val success = channelManager.addChannel(channel)
        if (success) {
            Toast.makeText(this, "Channel added successfully", Toast.LENGTH_SHORT).show()
            channelNameInput.text.clear()
            playlistUrlInput.text.clear()
            thumbnailUrlInput.text.clear()
            loadChannels()
        } else {
            Toast.makeText(this, "Channel already exists", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRemoveConfirmation(channel: Channel) {
        AlertDialog.Builder(this)
            .setTitle("Remove Channel")
            .setMessage("Are you sure you want to remove '${channel.name}'?")
            .setPositiveButton("Remove") { _, _ ->
                val success = channelManager.removeChannel(channel.id)
                if (success) {
                    Toast.makeText(this, "Channel removed", Toast.LENGTH_SHORT).show()
                    loadChannels()
                } else {
                    Toast.makeText(this, "Failed to remove channel", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
