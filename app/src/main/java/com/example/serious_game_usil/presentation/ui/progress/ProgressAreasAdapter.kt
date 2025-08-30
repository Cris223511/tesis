package com.example.serious_game_usil.presentation.ui.progress

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ProgressBarData
import com.example.serious_game_usil.data.TrendDirection
import com.google.android.material.progressindicator.LinearProgressIndicator

class ProgressAreasAdapter : RecyclerView.Adapter<ProgressAreasAdapter.AreaViewHolder>() {

    private var areas = listOf<ProgressBarData>()

    fun updateAreas(newAreas: List<ProgressBarData>) {
        areas = newAreas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AreaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_progress_area, parent, false)
        return AreaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AreaViewHolder, position: Int) {
        holder.bind(areas[position], position)
    }

    override fun getItemCount() = areas.size

    inner class AreaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textAreaName: TextView = itemView.findViewById(R.id.textAreaName)
        private val textCurrentValue: TextView = itemView.findViewById(R.id.textCurrentValue)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)
        private val progressBar: LinearProgressIndicator = itemView.findViewById(R.id.progressBar)
        private val iconTrend: ImageView = itemView.findViewById(R.id.iconTrend)

        fun bind(area: ProgressBarData, position: Int) {
            textAreaName.text = area.label
            textCurrentValue.text = "${area.currentValue}%"
            textDescription.text = area.description

            // Configurar color de la barra de progreso
            val progressColor = ContextCompat.getColor(itemView.context, area.colorRes)
            progressBar.setIndicatorColor(progressColor)
            
            // Configurar icono de tendencia
            val (trendIcon, trendColor) = when (area.trend) {
                TrendDirection.IMPROVING -> {
                    android.R.drawable.arrow_up_float to android.R.color.holo_green_dark
                }
                TrendDirection.DECLINING -> {
                    android.R.drawable.arrow_down_float to android.R.color.holo_red_dark
                }
                TrendDirection.STABLE -> {
                    android.R.drawable.ic_menu_view to android.R.color.darker_gray
                }
            }
            
            iconTrend.setImageResource(trendIcon)
            iconTrend.setColorFilter(ContextCompat.getColor(itemView.context, trendColor))

            // Animar la barra de progreso
            animateProgress(area.currentValue, position)
        }

        private fun animateProgress(targetProgress: Int, position: Int) {
            // Resetear progreso
            progressBar.progress = 0
            
            // Crear animación con delay escalonado
            val animator = ValueAnimator.ofInt(0, targetProgress)
            animator.duration = 1000 // 1 segundo
            animator.startDelay = (position * 200).toLong() // 200ms delay entre cada item
            
            animator.addUpdateListener { animation ->
                val progress = animation.animatedValue as Int
                progressBar.progress = progress
                textCurrentValue.text = "$progress%"
            }
            
            animator.start()
        }
    }
}