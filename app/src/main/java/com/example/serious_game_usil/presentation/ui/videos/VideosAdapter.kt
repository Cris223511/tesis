package com.example.serious_game_usil.presentation.ui.videos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.VideoEducativo

class VideosAdapter(
    private val onVideoClick: (VideoEducativo) -> Unit
) : ListAdapter<VideoEducativo, VideosAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_educativo, parent, false)
        return VideoViewHolder(view, onVideoClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        itemView: View,
        private val onVideoClick: (VideoEducativo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val videoThumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val videoTitle: TextView = itemView.findViewById(R.id.videoTitle)
        private val videoChannel: TextView = itemView.findViewById(R.id.videoChannel)
        private val videoCategory: TextView = itemView.findViewById(R.id.videoCategory)

        fun bind(video: VideoEducativo) {
            videoTitle.text = video.title
            videoChannel.text = video.channelTitle
            videoCategory.text = video.category

            // Cargar thumbnail con Glide
            Glide.with(itemView.context)
                .load(video.thumbnailUrl)
                .placeholder(R.drawable.ic_video_empty)
                .error(R.drawable.ic_video_empty)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(videoThumbnail)

            itemView.setOnClickListener {
                onVideoClick(video)
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoEducativo>() {
        override fun areItemsTheSame(oldItem: VideoEducativo, newItem: VideoEducativo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoEducativo, newItem: VideoEducativo): Boolean {
            return oldItem == newItem
        }
    }
}
