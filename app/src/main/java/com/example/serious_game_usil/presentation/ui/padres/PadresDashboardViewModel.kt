package com.example.serious_game_usil.presentation.ui.padres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.data.PatientStatsResponse
import com.example.serious_game_usil.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PadresDashboardViewModel : ViewModel() {
    private val patientRepository = PatientRepository()

    private val _myPatients = MutableStateFlow<List<PatientListItem>>(emptyList())
    val myPatients: StateFlow<List<PatientListItem>> = _myPatients.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPatientIndex = MutableStateFlow(0)
    val currentPatientIndex: StateFlow<Int> = _currentPatientIndex.asStateFlow()

    private val _currentPatientStats = MutableStateFlow<PatientStatsResponse?>(null)
    val currentPatientStats: StateFlow<PatientStatsResponse?> = _currentPatientStats.asStateFlow()

    fun loadMyPatients() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Usar el nuevo endpoint que devuelve solo los últimos 3 pacientes
                patientRepository.getLatestPatients().collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val patients = result.data
                            android.util.Log.d("PadresDashboard", "Loaded ${patients.size} latest patients")
                            _myPatients.value = patients
                            if (patients.isNotEmpty() && _currentPatientIndex.value >= patients.size) {
                                _currentPatientIndex.value = 0
                            }
                            // Cargar estadísticas del paciente actual después de cargar la lista
                            if (patients.isNotEmpty()) {
                                loadCurrentPatientStats()
                            }
                        }
                        is ApiResult.Error -> {
                            android.util.Log.e("PadresDashboard", "Error loading latest patients: ${result.message}")
                            // En caso de error, mantener lista vacía
                            _myPatients.value = emptyList()
                        }
                        is ApiResult.NetworkError -> {
                            android.util.Log.e("PadresDashboard", "Network error loading latest patients", result.exception)
                            // En caso de error de red, mantener lista vacía
                            _myPatients.value = emptyList()
                        }
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("PadresDashboard", "Exception loading latest patients", e)
                _myPatients.value = emptyList()
                _isLoading.value = false
            }
        }
    }

    fun getCurrentPatient(): PatientListItem? {
        val patients = _myPatients.value
        return if (patients.isNotEmpty() && _currentPatientIndex.value < patients.size) {
            patients[_currentPatientIndex.value]
        } else null
    }

    fun nextPatient() {
        val patients = _myPatients.value
        if (patients.isNotEmpty()) {
            _currentPatientIndex.value = (_currentPatientIndex.value + 1) % patients.size
        }
    }

    fun previousPatient() {
        val patients = _myPatients.value
        if (patients.isNotEmpty()) {
            _currentPatientIndex.value = if (_currentPatientIndex.value > 0) {
                _currentPatientIndex.value - 1
            } else {
                patients.size - 1
            }
        }
    }

    fun setCurrentPatientIndex(index: Int) {
        val patients = _myPatients.value
        if (index in 0 until patients.size) {
            _currentPatientIndex.value = index
            // Cargar estadísticas del nuevo paciente seleccionado
            loadCurrentPatientStats()
        }
    }

    fun loadCurrentPatientStats() {
        val currentPatient = getCurrentPatient()
        if (currentPatient != null) {
            viewModelScope.launch {
                patientRepository.getPatientStats(currentPatient.id).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            android.util.Log.d("PadresDashboard", "Stats loaded for patient ${currentPatient.id}: ${result.data}")
                            _currentPatientStats.value = result.data
                        }
                        is ApiResult.Error -> {
                            android.util.Log.e("PadresDashboard", "Error loading patient stats: ${result.message}")
                            _currentPatientStats.value = null
                        }
                        is ApiResult.NetworkError -> {
                            android.util.Log.e("PadresDashboard", "Network error loading patient stats", result.exception)
                            _currentPatientStats.value = null
                        }
                    }
                }
            }
        }
    }
}