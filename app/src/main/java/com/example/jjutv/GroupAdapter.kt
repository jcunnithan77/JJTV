package com.example.jjutv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class GroupAdapter(
    private val groups: List<VideoGroup>,
    private val context: Context
) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    private var selectedPosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.groupThumbnail)
        val title: TextView = view.findViewById(R.id.groupTitle)
        val root: View = view.findViewById(R.id.groupCardRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.group_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val group = groups[position]
        holder.title.text = group.title
        Picasso.get().load(group.thumbnail).into(holder.imageView)

        // Highlight border if selected
        val backgroundRes = if (position == selectedPosition)
            R.drawable.bg_group_card_selected
        else
            R.drawable.bg_group_card_normal
        holder.root.setBackgroundResource(backgroundRes)

        holder.itemView.setOnClickListener {
            selectedPosition = position
            notifyDataSetChanged()

            val intent = Intent(context, GroupActivity::class.java)
            intent.putExtra("videos", ArrayList(group.videos))
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

    override fun getItemCount(): Int = groups.size
}
