package com.example.jjutv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ChannelManager(private val context: Context) {

    private val channelsFile = File(context.filesDir, "channels.json")

    init {
        // Initialize with default channels from assets if no file exists
        if (!channelsFile.exists()) {
            initializeDefaultChannels()
        }
    }

    private fun initializeDefaultChannels() {
        try {
            val defaultJson = context.assets.open("channels.json").bufferedReader().use { it.readText() }
            channelsFile.writeText(defaultJson)
        } catch (e: IOException) {
            e.printStackTrace()
            // Create empty channels file if assets file doesn't exist
            val emptyJson = JSONObject().apply {
                put("channels", JSONArray())
            }.toString()
            channelsFile.writeText(emptyJson)
        }
    }

    fun getChannels(): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val jsonString = channelsFile.readText()
            val jsonObject = JSONObject(jsonString)
            val channelsArray = jsonObject.getJSONArray("channels")

            for (i in 0 until channelsArray.length()) {
                val channelObj = channelsArray.getJSONObject(i)
                channels.add(
                    Channel(
                        id = channelObj.getString("id"),
                        name = channelObj.getString("name"),
                        thumbnail = channelObj.getString("thumbnail"),
                        playlistUrl = channelObj.getString("playlistUrl")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    fun addChannel(channel: Channel): Boolean {
        return try {
            val jsonString = channelsFile.readText()
            val jsonObject = JSONObject(jsonString)
            val channelsArray = jsonObject.getJSONArray("channels")

            // Check if channel already exists
            for (i in 0 until channelsArray.length()) {
                val existingChannel = channelsArray.getJSONObject(i)
                if (existingChannel.getString("playlistUrl") == channel.playlistUrl) {
                    return false // Channel already exists
                }
            }

            // Add new channel
            val newChannelObj = JSONObject().apply {
                put("id", channel.id)
                put("name", channel.name)
                put("thumbnail", channel.thumbnail)
                put("playlistUrl", channel.playlistUrl)
            }
            channelsArray.put(newChannelObj)

            // Save to file
            channelsFile.writeText(jsonObject.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeChannel(channelId: String): Boolean {
        return try {
            val jsonString = channelsFile.readText()
            val jsonObject = JSONObject(jsonString)
            val channelsArray = jsonObject.getJSONArray("channels")
            val newArray = JSONArray()

            for (i in 0 until channelsArray.length()) {
                val channel = channelsArray.getJSONObject(i)
                if (channel.getString("id") != channelId) {
                    newArray.put(channel)
                }
            }

            jsonObject.put("channels", newArray)
            channelsFile.writeText(jsonObject.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
