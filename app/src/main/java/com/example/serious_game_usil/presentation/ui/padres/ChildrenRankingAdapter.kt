package com.example.serious_game_usil.presentation.ui.padres

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView

class ChildrenRankingAdapter(
    private val onChildClick: (ChildRankingItem) -> Unit
) : RecyclerView.Adapter<ChildrenRankingAdapter.ChildViewHolder>() {

    private var children = listOf<ChildRankingItem>()

    fun updateChildren(newChildren: List<ChildRankingItem>) {
        children = newChildren
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child_ranking, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        holder.bind(children[position])
    }

    override fun getItemCount() = children.size

    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardChild)
        private val imageChild: ShapeableImageView = itemView.findViewById(R.id.imageChild)
        private val textChildName: TextView = itemView.findViewById(R.id.textChildName)
        private val textChildAge: TextView = itemView.findViewById(R.id.textChildAge)
        private val textWeight: TextView = itemView.findViewById(R.id.textWeight)
        private val textHeight: TextView = itemView.findViewById(R.id.textHeight)
        private val buttonDetails: MaterialButton = itemView.findViewById(R.id.buttonVerDetalles)

        fun bind(child: ChildRankingItem) {
            textChildName.text = child.name
            textChildAge.text = child.age
            textWeight.text = "Weight ${child.weight}"
            textHeight.text = "Height ${child.height}"

            // Configurar imagen del niño
            if (child.photoUrl != null) {
                // Cargar imagen real si está disponible
                // Glide.with(itemView.context).load(child.photoUrl).into(imageChild)
            } else {
                // Usar placeholder
                imageChild.setImageResource(R.drawable.ic_child)
            }

            // Configurar color del card según el puntaje
            val cardColor = when {
                child.overallScore >= 80 -> R.color.green_500
                child.overallScore >= 60 -> R.color.orange
                child.overallScore >= 40 -> R.color.warning_color
                else -> R.color.red_500
            }
            
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, cardColor)
            )

            // Click listeners
            cardView.setOnClickListener {
                onChildClick(child)
            }

            buttonDetails.setOnClickListener {
                onChildClick(child)
            }

            // Animación de entrada
            itemView.alpha = 0f
            itemView.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(bindingAdapterPosition * 100L)
                .start()
        }
    }
}

data class ChildRankingItem(
    val id: Int,
    val name: String,
    val age: String,
    val weight: String,
    val height: String,
    val overallScore: Int, // 0-100
    val photoUrl: String? = null
)