package com.example.serious_game_usil.presentation.ui.padres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.data.PatientStatsResponse
import com.example.serious_game_usil.repository.PatientRepository
import com.example.serious_game_usil.guards.AuthManager
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
                // Verificar si el usuario es administrador o tiene rol de cuidador
                val isAdmin = AuthManager.isAdmin()
                val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
                val hasCaregiver = isAdmin || userRoles.contains("cuidador")

                if (!hasCaregiver) {
                    android.util.Log.w("PadresDashboard", "User does not have caregiver role and is not admin")
                    _myPatients.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                // Obtener el nombre del usuario actual para filtrar por cuidador
                val currentUserName = AuthManager.getNombresApellidos()

                // Usar el endpoint de pacientes y filtrar por cuidador
                patientRepository.getPatients(page = 1, limit = 100).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val allPatients = result.data.patients ?: emptyList()

                            // Los administradores ven TODOS los pacientes, sin filtrar
                            val filteredPatients = if (isAdmin) {
                                android.util.Log.d("PadresDashboard", "Admin detected - showing ALL ${allPatients.size} patients")
                                allPatients
                            } else {
                                // Solo los cuidadores normales filtran por su nombre
                                allPatients.filter { patient ->
                                    patient.cuidadorNombre == currentUserName
                                }
                            }

                            android.util.Log.d("PadresDashboard", "Loaded ${filteredPatients.size} patients (isAdmin: $isAdmin, user: $currentUserName)")
                            _myPatients.value = filteredPatients
                            if (filteredPatients.isNotEmpty() && _currentPatientIndex.value >= filteredPatients.size) {
                                _currentPatientIndex.value = 0
                            }
                            // Cargar estadísticas del paciente actual después de cargar la lista
                            if (filteredPatients.isNotEmpty()) {
                                loadCurrentPatientStats()
                            }
                        }
                        is ApiResult.Error -> {
                            android.util.Log.e("PadresDashboard", "Error loading patients for caregiver: ${result.message}")
                            // En caso de error, mantener lista vacía
                            _myPatients.value = emptyList()
                        }
                        is ApiResult.NetworkError -> {
                            android.util.Log.e("PadresDashboard", "Network error loading patients for caregiver", result.exception)
                            // En caso de error de red, mantener lista vacía
                            _myPatients.value = emptyList()
                        }
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("PadresDashboard", "Exception loading patients for caregiver", e)
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