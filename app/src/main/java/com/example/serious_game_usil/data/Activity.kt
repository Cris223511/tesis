package com.example.serious_game_usil.data

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class Activity(
    val id: Long,
    val title: String,
    val subtitle: String,
    @ColorInt val colorStart: Int,
    @ColorInt val colorEnd: Int,
    @DrawableRes val iconResId: Int,
    val description: String? = null,
    val durationMinutes: Int = 0,
    val difficulty: ActivityDifficulty = ActivityDifficulty.MEDIUM,
    val category: ActivityCategory = ActivityCategory.COGNITIVE
)

enum class ActivityDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class ActivityCategory {
    COGNITIVE,
    MOTOR,
    SOCIAL,
    EMOTIONAL,
    SENSORY
}