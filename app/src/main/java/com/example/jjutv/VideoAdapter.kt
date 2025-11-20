package com.example.jjutv

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val context: Context
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val videoTitle: TextView = view.findViewById(R.id.videoTitle)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.video_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.videoTitle.text = video.title
        Picasso.get().load(video.thumbnail).into(holder.videoThumbnail)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra("videoId", video.id)
            context.startActivity(intent)
        }
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            view.pivotY = 0f // anchor from top
            view.animate()
                .scaleX(if (hasFocus) 1.01f else 1f)
                .scaleY(if (hasFocus) 1.01f else 1f)
                .translationY(if (hasFocus) -5f else 0f)
                .setDuration(150)
                .start()

        }


    }



    override fun getItemCount(): Int = videos.size
}
