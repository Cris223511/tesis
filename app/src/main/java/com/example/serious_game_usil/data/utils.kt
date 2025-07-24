package com.example.serious_game_usil.data


data class Statistics(
    val stimulusHours: Int,
    val stimulusMinutes: Int,
    val pendingHours: Int,
    val pendingMinutes: Int
)

data class MonthlyStatistics(
    val currentMonth: MonthStats,
    val previousMonth1: MonthStats,
    val previousMonth2: MonthStats
)

data class MonthStats(
    val monthName: String,
    val hoursCompleted: Int,
    val activitiesCompleted: Int
)

data class ActivityHistoryItem(
    val date: Long,
    val completedActivities: List<Long>,
    val totalMinutes: Int
)