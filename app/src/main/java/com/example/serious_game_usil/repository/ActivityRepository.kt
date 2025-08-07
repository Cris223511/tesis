package com.example.serious_game_usil.repository

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.Activity
import com.example.serious_game_usil.data.ActivityCategory
import com.example.serious_game_usil.data.ActivityDifficulty
import com.example.serious_game_usil.data.ActivityHistoryItem
import com.example.serious_game_usil.data.MonthStats
import com.example.serious_game_usil.data.MonthlyStatistics
import com.example.serious_game_usil.data.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.reflect.Array.getInt
import java.util.Calendar

class ActivityRepository private constructor(private val context: Context) : IActivityRepository {

    private val sharedPreferences = context.getSharedPreferences("activity_prefs", Context.MODE_PRIVATE)

    // Cache temporal para actividades
    private var activitiesCache: List<Activity>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 minutos

    override suspend fun getRecommendedActivities(): List<Activity> = withContext(Dispatchers.IO) {
        // Verificar cache
        if (activitiesCache != null &&
            System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION) {
            return@withContext activitiesCache!!
        }

        // Simular llamada a API
        delay(300)

        // En producción, esto vendría de una API o base de datos
        val activities = listOf(
            Activity(
                id = 1,
                title = "Mi cuerpo",
                subtitle = "Sara M.",
                colorStart = ContextCompat.getColor(context, R.color.primary),
                colorEnd = ContextCompat.getColor(context, R.color.secondary),
                iconResId = R.drawable.ic_child,
                description = "Aprende sobre las partes del cuerpo de forma divertida",
                durationMinutes = 15,
                difficulty = ActivityDifficulty.EASY,
                category = ActivityCategory.COGNITIVE
            ),
            Activity(
                id = 2,
                title = "Yo siento",
                subtitle = "Ana P.",
                colorStart = ContextCompat.getColor(context, R.color.error),
                colorEnd = ContextCompat.getColor(context, R.color.error),
                iconResId = R.drawable.ic_person,
                description = "Identifica y expresa emociones",
                durationMinutes = 20,
                difficulty = ActivityDifficulty.MEDIUM,
                category = ActivityCategory.EMOTIONAL
            ),
            Activity(
                id = 3,
                title = "Manos a la obra",
                subtitle = "Luis G.",
                colorStart = ContextCompat.getColor(context, R.color.secondary),
                colorEnd = ContextCompat.getColor(context, R.color.primary),
                iconResId = R.drawable.ic_person,
                description = "Actividades de motricidad fina",
                durationMinutes = 25,
                difficulty = ActivityDifficulty.MEDIUM,
                category = ActivityCategory.MOTOR
            )
        )

        // Actualizar cache
        activitiesCache = activities
        cacheTimestamp = System.currentTimeMillis()

        activities
    }

    override suspend fun getActivityById(id: Long): Activity? = withContext(Dispatchers.IO) {
        val activities = getRecommendedActivities()
        activities.find { it.id == id }
    }

    override suspend fun getTodayStatistics(): Statistics = withContext(Dispatchers.IO) {
        // Simular carga de estadísticas
        delay(200)

        // En producción, esto vendría de la base de datos
        val todayKey = getTodayKey()
        val stimulusMinutes = sharedPreferences.getInt("stimulus_minutes_$todayKey", 1208) // 20:08
        val pendingMinutes = sharedPreferences.getInt("pending_minutes_$todayKey", 0)

        Statistics(
            stimulusHours = stimulusMinutes / 60,
            stimulusMinutes = stimulusMinutes % 60,
            pendingHours = pendingMinutes / 60,
            pendingMinutes = pendingMinutes % 60
        )
    }

    override suspend fun getMonthlyStatistics(): MonthlyStatistics = withContext(Dispatchers.IO) {
        delay(300)

        // Datos de ejemplo para los últimos 3 meses
        MonthlyStatistics(
            currentMonth = MonthStats("Julio", 45, 12),
            previousMonth1 = MonthStats("Junio", 38, 15),
            previousMonth2 = MonthStats("Mayo", 42, 10)
        )
    }


    override suspend fun logActivitySelection(activityId: Long) {
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            sharedPreferences.edit().apply {
                putLong("last_activity_selected", activityId)
                putLong("last_activity_timestamp", timestamp)

                // Incrementar contador de selecciones
                val selectionKey = "activity_selections_$activityId"
                val currentCount = getInt(selectionKey, 0)
                putInt(selectionKey, currentCount + 1)

                apply()
            }
        }
    }

    override suspend fun startActivity(activityId: Long): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = System.currentTimeMillis()
            sharedPreferences.edit().apply {
                putLong("current_activity_id", activityId)
                putLong("current_activity_start", startTime)
                putBoolean("activity_in_progress", true)
                apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun completeActivity(activityId: Long, score: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = sharedPreferences.getLong("current_activity_start", 0L)
            val duration = System.currentTimeMillis() - startTime
            val todayKey = getTodayKey()

            sharedPreferences.edit().apply {
                // Registrar completación
                putBoolean("activity_completed_${activityId}_$todayKey", true)
                putInt("activity_score_${activityId}_$todayKey", score)
                putLong("activity_duration_${activityId}_$todayKey", duration)

                // Actualizar tiempo de estímulo del día
                val currentMinutes = getInt("stimulus_minutes_$todayKey", 0)
                putInt("stimulus_minutes_$todayKey", currentMinutes + (duration / 60000).toInt())

                // Limpiar actividad en progreso
                remove("current_activity_id")
                remove("current_activity_start")
                putBoolean("activity_in_progress", false)

                apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getActivityHistory(days: Int): List<ActivityHistoryItem> = withContext(Dispatchers.IO) {
        delay(200)

        val history = mutableListOf<ActivityHistoryItem>()
        val calendar = Calendar.getInstance()

        for (i in 0 until days) {
            val dayKey = getDayKey(calendar.timeInMillis)
            val completedActivities = getCompletedActivitiesForDay(dayKey)

            if (completedActivities.isNotEmpty()) {
                history.add(
                    ActivityHistoryItem(
                        date = calendar.timeInMillis,
                        completedActivities = completedActivities,
                        totalMinutes = getTotalMinutesForDay(dayKey)
                    )
                )
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        history
    }

    private fun getTodayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun getDayKey(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun getCompletedActivitiesForDay(dayKey: String): List<Long> {
        val activities = mutableListOf<Long>()
        for (i in 1L..5L) {
            if (sharedPreferences.getBoolean("activity_completed_${i}_$dayKey", false)) {
                activities.add(i)
            }
        }
        return activities
    }

    private fun getTotalMinutesForDay(dayKey: String): Int {
        return sharedPreferences.getInt("stimulus_minutes_$dayKey", 0)
    }

    companion object {
        @Volatile
        private var INSTANCE: ActivityRepository? = null

        fun getInstance(context: Context): ActivityRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivityRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}