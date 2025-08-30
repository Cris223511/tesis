package com.example.serious_game_usil.presentation.ui.progress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.MonthlyProgress
import com.google.android.material.card.MaterialCardView

class MonthlyProgressAdapter(
    private val onMonthClick: (MonthlyProgress) -> Unit
) : RecyclerView.Adapter<MonthlyProgressAdapter.MonthViewHolder>() {

    private var months = listOf<MonthlyProgress>()

    fun updateMonths(newMonths: List<MonthlyProgress>) {
        months = newMonths
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monthly_progress, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(months[position], position == 0)
    }

    override fun getItemCount() = months.size

    inner class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardMonth: MaterialCardView = itemView.findViewById(R.id.cardMonth)
        private val textMonth: TextView = itemView.findViewById(R.id.textMonth)
        private val textScore: TextView = itemView.findViewById(R.id.textScore)
        private val textSessions: TextView = itemView.findViewById(R.id.textSessions)
        private val iconTrend: ImageView = itemView.findViewById(R.id.iconTrend)

        fun bind(month: MonthlyProgress, isCurrentMonth: Boolean) {
            textMonth.text = month.month
            
            if (month.totalSessions == 0) {
                textScore.text = "Sin datos"
                textSessions.text = "0 sesiones"
                cardMonth.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                )
                iconTrend.setImageResource(android.R.drawable.ic_dialog_info)
            } else {
                textScore.text = "${month.overallScore}"
                textSessions.text = "${month.totalSessions} sesiones"
                
                val backgroundColor = when {
                    isCurrentMonth -> R.color.primary
                    month.overallScore >= 80 -> android.R.color.holo_green_light
                    month.overallScore >= 60 -> android.R.color.holo_orange_light
                    else -> android.R.color.holo_red_light
                }
                
                cardMonth.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, backgroundColor)
                )
                
                val trendIconRes = when(month.trend) {
                    "improving" -> android.R.drawable.arrow_up_float
                    "declining" -> android.R.drawable.arrow_down_float
                    else -> android.R.drawable.ic_menu_view
                }
                iconTrend.setImageResource(trendIconRes)
            }

            cardMonth.setOnClickListener {
                onMonthClick(month)
            }
        }
    }
}