package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.Activity
import com.example.serious_game_usil.data.ActivityHistoryItem
import com.example.serious_game_usil.data.MonthlyStatistics
import com.example.serious_game_usil.data.Statistics

interface IActivityRepository {

    suspend fun getRecommendedActivities(): List<Activity>

    suspend fun getActivityById(id: Long): Activity?

    suspend fun getTodayStatistics(): Statistics

    suspend fun getMonthlyStatistics(): MonthlyStatistics

    suspend fun logActivitySelection(activityId: Long)

    suspend fun startActivity(activityId: Long): Boolean

    suspend fun completeActivity(activityId: Long, score: Int = 0): Boolean

    suspend fun getActivityHistory(days: Int = 7): List<ActivityHistoryItem>
}