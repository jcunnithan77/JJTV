package com.example.jjutv

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GroupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        val videoList = intent.getSerializableExtra("videos") as? ArrayList<VideoItem> ?: arrayListOf()

        val recyclerView: RecyclerView = findViewById(R.id.videoList)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = VideoAdapter(videoList, this)
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

    }
}