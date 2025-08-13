package com.example.serious_game_usil.presentation.ui.progress

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ThreeMonthComparison
import com.example.serious_game_usil.data.MonthlyProgress
import com.example.serious_game_usil.data.AutismProgressMetrics
import com.example.serious_game_usil.`interface`.ApiService
import RetrofitClient
import kotlinx.coroutines.launch
import android.util.Log

class ProgressViewModel : ViewModel() {
    
    private val apiService = RetrofitClient.getApiService()
    
    // LiveData para la comparación de 3 meses
    private val _threeMonthComparison = MutableLiveData<ThreeMonthComparison?>()
    val threeMonthComparison: LiveData<ThreeMonthComparison?> = _threeMonthComparison
    
    // LiveData para progreso de todos los hijos
    private val _allChildrenProgress = MutableLiveData<List<ThreeMonthComparison>>()
    val allChildrenProgress: LiveData<List<ThreeMonthComparison>> = _allChildrenProgress
    
    // LiveData para estado de carga
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // LiveData para manejo de errores
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    fun loadThreeMonthComparison(childId: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val response = apiService.getThreeMonthComparison(childId)
                
                if (response.isSuccessful) {
                    val comparison = response.body()
                    if (comparison != null) {
                        _threeMonthComparison.value = comparison
                    } else {
                        // Crear estructura vacía con ceros si no hay datos
                        _threeMonthComparison.value = createEmptyComparison(childId)
                    }
                } else {
                    when (response.code()) {
                        404 -> {
                            // No hay datos, mostrar estructura con ceros
                            _threeMonthComparison.value = createEmptyComparison(childId)
                        }
                        else -> {
                            _error.value = "Error ${response.code()}: ${response.message()}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProgressViewModel", "Error loading progress", e)
                // En caso de error, mostrar estructura vacía
                _threeMonthComparison.value = createEmptyComparison(childId)
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadAllChildrenProgress() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val response = apiService.getAllChildrenProgress()
                
                if (response.isSuccessful) {
                    val progressList = response.body() ?: emptyList()
                    _allChildrenProgress.value = progressList
                } else {
                    _error.value = "Error ${response.code()}: ${response.message()}"
                    _allChildrenProgress.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProgressViewModel", "Error loading all children progress", e)
                _error.value = "Error de conexión: ${e.message}"
                _allChildrenProgress.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun createEmptyComparison(childId: Int): ThreeMonthComparison {
        // Crear estructura vacía con todos los valores en 0
        val emptyMonths = listOf(
            createEmptyMonth("Este mes"),
            createEmptyMonth("Mes anterior"), 
            createEmptyMonth("Hace 2 meses")
        )
        
        val emptyMetrics = AutismProgressMetrics(
            childId = childId,
            year = 2024,
            month = 1,
            avgSocialInteraction = 0.0,
            avgCommunication = 0.0,
            avgSensoryProcessing = 0.0,
            avgAttentionFocus = 0.0,
            avgEmotionalRegulation = 0.0,
            avgMotorSkills = 0.0,
            avgProblemSolving = 0.0,
            totalSessions = 0,
            completedSessions = 0,
            meltdownCount = 0
        )
        
        return ThreeMonthComparison(
            childId = childId,
            childName = "Usuario",
            currentMonth = emptyMetrics,
            months = emptyMonths,
            summary = com.example.serious_game_usil.data.ProgressSummary(
                overallTrend = "stable",
                strongestAreas = emptyList(),
                improvingAreas = emptyList(),
                areasNeedingWork = listOf("Todas las áreas necesitan desarrollo"),
                totalSessions3M = 0,
                avgSessionTime = 0
            ),
            recommendations = listOf(
                "Comenzar con actividades básicas de interacción social",
                "Iniciar sesiones cortas y regulares",
                "Registrar progreso después de cada sesión"
            )
        )
    }
    
    private fun createEmptyMonth(monthName: String): MonthlyProgress {
        return MonthlyProgress(
            month = monthName,
            year = 2024,
            overallScore = 0,
            trend = "stable",
            totalSessions = 0,
            areas = listOf(
                com.example.serious_game_usil.data.AreaProgress("Interacción Social", 0, 0.0),
                com.example.serious_game_usil.data.AreaProgress("Comunicación", 0, 0.0),
                com.example.serious_game_usil.data.AreaProgress("Procesamiento Sensorial", 0, 0.0),
                com.example.serious_game_usil.data.AreaProgress("Atención", 0, 0.0),
                com.example.serious_game_usil.data.AreaProgress("Regulación Emocional", 0, 0.0)
            )
        )
    }
    
    fun clearError() {
        _error.value = null
    }
}