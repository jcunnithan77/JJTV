package com.example.jjutv

import java.io.Serializable

data class Channel(
    val id: String,
    val name: String,
    val thumbnail: String,
    val playlistUrl: String
) : Serializable

data class VideoGroup(
    val title: String,
    val thumbnail: String,
    val videos: List<VideoItem>
) : Serializable

data class VideoItem(
    val id: String,
    val thumbnail: String,
    val title:String
) : Serializable
