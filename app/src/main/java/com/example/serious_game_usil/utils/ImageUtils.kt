package com.example.serious_game_usil.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.serious_game_usil.R

object ImageUtils {
    
    /**
     * Carga la foto del usuario usando Glide con configuración consistente
     */
    fun loadUserPhoto(
        context: Context,
        imageUrl: String?,
        imageView: ImageView,
        placeholder: Int = R.drawable.ic_person
    ) {
        if (!imageUrl.isNullOrEmpty() && 
            imageUrl != "null" && 
            imageUrl.isNotBlank() && 
            imageUrl != "undefined") {
            
            android.util.Log.d("ImageUtils", "Loading image: $imageUrl")
            
            Glide.with(context)
                .load(imageUrl)
                .placeholder(placeholder)
                .error(placeholder)
                .circleCrop()
                .into(imageView)
        } else {
            android.util.Log.d("ImageUtils", "Using default placeholder for empty/null URL: '$imageUrl'")
            imageView.setImageResource(placeholder)
        }
    }
    
    /**
     * Verifica si la URL de imagen es válida
     */
    fun isValidImageUrl(imageUrl: String?): Boolean {
        return !imageUrl.isNullOrEmpty() && 
               imageUrl != "null" && 
               imageUrl.isNotBlank() && 
               imageUrl != "undefined"
    }
}