package com.example.serious_game_usil.presentation.ui.progress

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ThreeMonthComparison
import com.example.serious_game_usil.data.MonthlyProgress
import com.example.serious_game_usil.data.AutismProgressMetrics
import com.example.serious_game_usil.`interface`.ApiService

import kotlinx.coroutines.launch
import android.util.Log
import com.example.serious_game_usil.network.RetrofitClient
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.data.Caregiver
import com.example.serious_game_usil.guards.AuthManager

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

    // LiveData para pacientes
    private val _patients = MutableLiveData<List<PatientListItem>>()
    val patients: LiveData<List<PatientListItem>> = _patients

    // LiveData para cuidadores
    private val _caregivers = MutableLiveData<List<Caregiver>>()
    val caregivers: LiveData<List<Caregiver>> = _caregivers
    
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
                        _threeMonthComparison.value = createEmptyComparison(buildPlaceholderPatient(childId))
                    }
                } else {
                    when (response.code()) {
                        404 -> {
                            // No hay datos, mostrar estructura con ceros
                            _threeMonthComparison.value = createEmptyComparison(buildPlaceholderPatient(childId))
                        }
                        else -> {
                            _error.value = "Error ${response.code()}: ${response.message()}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProgressViewModel", "Error loading progress", e)
                // En caso de error, mostrar estructura vacía
                _threeMonthComparison.value = createEmptyComparison(buildPlaceholderPatient(childId))
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
                val patients = fetchPatientsForCurrentUser()
                if (patients.isEmpty()) {
                    _allChildrenProgress.value = emptyList()
                    return@launch
                }

                val progressList = mutableListOf<ThreeMonthComparison>()

                patients.forEach { patient ->
                    try {
                        val response = apiService.getThreeMonthComparison(patient.id)
                        when {
                            response.isSuccessful && response.body() != null -> {
                                progressList += response.body()!!
                            }
                            response.code() == 404 -> {
                                progressList += createEmptyComparison(patient)
                            }
                            else -> {
                                Log.w("ProgressViewModel", "No se pudo cargar progreso para paciente ${patient.id}: ${response.code()}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProgressViewModel", "Error loading progress for patient ${patient.id}", e)
                    }
                }

                _allChildrenProgress.value = progressList.sortedBy { it.childName.lowercase() }
                if (progressList.isEmpty()) {
                    _error.value = "No se pudo recuperar información de progreso en este momento"
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
    
    private suspend fun fetchPatientsForCurrentUser(): List<PatientListItem> {
        val response = when (getNormalizedRole()) {
            "administrador", "admin" -> apiService.getPatients(limit = 100)
            "cuidador", "responsable", "pd" -> apiService.getMyPatients(limit = 100)
            else -> apiService.getPatients(limit = 100)
        }

        return if (response.isSuccessful) {
            response.body()?.patients.orEmpty()
        } else {
            _error.value = when (response.code()) {
                403 -> "No tiene permisos para ver esta información"
                404 -> "No se encontraron pacientes"
                else -> "Error cargando pacientes: ${response.code()}"
            }
            emptyList()
        }
    }

    private fun createEmptyComparison(patient: PatientListItem): ThreeMonthComparison {
        val emptyMonths = listOf(
            createEmptyMonth("Este mes"),
            createEmptyMonth("Mes anterior"), 
            createEmptyMonth("Hace 2 meses")
        )

        val emptyMetrics = AutismProgressMetrics(
            childId = patient.id,
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
            childId = patient.id,
            childName = patient.nombresApellidos,
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
    
    fun loadPatients() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val response = when(getNormalizedRole()) {
                    "administrador", "admin" -> apiService.getPatients(limit = 100)
                    "cuidador", "responsable", "pd" -> apiService.getMyPatients(limit = 100)
                    else -> apiService.getPatients(limit = 100)
                }

                if (response.isSuccessful) {
                    val patientsResponse = response.body()
                    _patients.value = patientsResponse?.patients ?: emptyList()
                } else {
                    when(response.code()) {
                        403 -> _error.value = "No tiene permisos para ver esta información"
                        404 -> _error.value = "No se encontraron pacientes"
                        else -> _error.value = "Error cargando pacientes: ${response.code()}"
                    }
                    _patients.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProgressViewModel", "Error loading patients", e)
                _error.value = "Error de conexión"
                _patients.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCaregivers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                if (getNormalizedRole() !in setOf("administrador", "admin")) {
                    _caregivers.value = emptyList()
                    return@launch
                }

                val response = apiService.getCaregivers()

                if (response.isSuccessful) {
                    val caregiversResponse = response.body()
                    _caregivers.value = caregiversResponse?.caregivers ?: emptyList()
                } else {
                    _caregivers.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProgressViewModel", "Error loading caregivers", e)
                _caregivers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun buildPlaceholderPatient(childId: Int): PatientListItem {
        return PatientListItem(
            id = childId,
            serialId = childId.toString(),
            nombresApellidos = "Paciente",
            tipoDocumento = "",
            numDocumento = "",
            edad = 0,
            sexo = "",
            terapeutaNombre = "",
            cuidadorNombre = null,
            activo = true,
            fotoMovil = null
        )
    }

    private fun getNormalizedRole(): String {
        return AuthManager.getUserRole()?.trim()?.lowercase().orEmpty()
    }
}
