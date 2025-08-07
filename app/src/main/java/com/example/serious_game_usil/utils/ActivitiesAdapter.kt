package com.example.serious_game_usil.utils

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.Activity
import com.example.serious_game_usil.data.ActivityCategory
import com.example.serious_game_usil.data.ActivityDifficulty
import com.google.android.material.card.MaterialCardView

class ActivitiesAdapter(
    private var activities: List<Activity>,
    private val onActivityClick: (Activity) -> Unit
) : RecyclerView.Adapter<ActivitiesAdapter.ActivityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_pager, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(activities[position])
    }

    override fun getItemCount(): Int = activities.size

    fun updateActivities(newActivities: List<Activity>) {
        activities = newActivities
        notifyDataSetChanged()
    }

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: MaterialCardView = itemView.findViewById(R.id.cardContainer)
        private val gradientBackground: View = itemView.findViewById(R.id.gradientBackground)
        private val activityIcon: ImageView = itemView.findViewById(R.id.activityIcon)
        private val activityTitle: TextView = itemView.findViewById(R.id.activityTitle)
        private val activitySubtitle: TextView = itemView.findViewById(R.id.activitySubtitle)
        private val durationText: TextView = itemView.findViewById(R.id.durationText)
        private val difficultyText: TextView = itemView.findViewById(R.id.difficultyText)
        private val categoryChip: TextView = itemView.findViewById(R.id.categoryChip)

        fun bind(activity: Activity) {
            activityTitle.text = activity.title
            activitySubtitle.text = activity.subtitle
            activityIcon.setImageResource(activity.iconResId)
            durationText.text = "${activity.durationMinutes} min"

            difficultyText.text = when (activity.difficulty) {
                ActivityDifficulty.EASY -> "Fácil"
                ActivityDifficulty.MEDIUM -> "Medio"
                ActivityDifficulty.HARD -> "Difícil"
            }

            val difficultyColor = when (activity.difficulty) {
                ActivityDifficulty.EASY -> itemView.context.getColor(R.color.success_color)
                ActivityDifficulty.MEDIUM -> itemView.context.getColor(R.color.warning_color)
                ActivityDifficulty.HARD -> itemView.context.getColor(R.color.error_color)
            }
            difficultyText.setTextColor(difficultyColor)

            // Aquí está la corrección - usa ActivityCategory directamente
            categoryChip.text = when (activity.category) {
                ActivityCategory.COGNITIVE -> "Cognitivo"
                ActivityCategory.MOTOR -> "Motor"
                ActivityCategory.SOCIAL -> "Social"
                ActivityCategory.EMOTIONAL -> "Emocional"
                ActivityCategory.SENSORY -> "Sensorial"
            }

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(activity.colorStart, activity.colorEnd)
            )
            gradientDrawable.cornerRadius = 16f * itemView.context.resources.displayMetrics.density
            gradientBackground.background = gradientDrawable

            cardContainer.setOnClickListener {
                onActivityClick(activity)
            }
        }
    }
}