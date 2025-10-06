package com.example.serious_game_usil.presentation.ui.therapy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.`interface`.*
import com.example.serious_game_usil.repository.TherapySessionRepository
import com.example.serious_game_usil.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TherapySessionViewModel : ViewModel() {
    private val repository = TherapySessionRepository()
    private val patientRepository = PatientRepository()

    // Estado para lista de sesiones
    private val _sessions = MutableStateFlow<List<TherapySession>>(emptyList())
    val sessions: StateFlow<List<TherapySession>> = _sessions.asStateFlow()

    // Estado para filtrado
    private val _allSessions = MutableStateFlow<List<TherapySession>>(emptyList())
    var currentFilter: String? = null
    private var currentSearchQuery: String? = null

    // Estado para sesiones paginadas
    private val _paginatedSessions = MutableStateFlow<PaginatedSessionsData?>(null)
    val paginatedSessions: StateFlow<PaginatedSessionsData?> = _paginatedSessions.asStateFlow()

    // Estado para sesión individual
    private val _currentSession = MutableStateFlow<TherapySession?>(null)
    val currentSession: StateFlow<TherapySession?> = _currentSession.asStateFlow()

    // Estado para detalles completos de sesión
    private val _sessionDetail = MutableStateFlow<com.example.serious_game_usil.data.SessionDetail?>(null)
    val sessionDetail: StateFlow<com.example.serious_game_usil.data.SessionDetail?> = _sessionDetail.asStateFlow()

    // Estado para terapeutas disponibles
    private val _availableTherapists = MutableStateFlow<List<UserListItem>>(emptyList())
    val availableTherapists: StateFlow<List<UserListItem>> = _availableTherapists.asStateFlow()

    // Estado para pacientes
    private val _patients = MutableStateFlow<List<PatientListItem>>(emptyList())
    val patients: StateFlow<List<PatientListItem>> = _patients.asStateFlow()

    // Estado para resultado de creación
    private val _createResult = MutableStateFlow(false)
    val createResult: StateFlow<Boolean> = _createResult.asStateFlow()

    // Estados de UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Estados para paginación
    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<String?>(null)
    val selectedStatus: StateFlow<String?> = _selectedStatus.asStateFlow()

    fun loadAllSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getSessions().collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _allSessions.value = result.data
                        applyCurrentFilter(result.data)
                        android.util.Log.d("TherapyViewModel", "Loaded ${result.data.size} sessions")
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error loading sessions: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun filterSessions(status: String?) {
        currentFilter = status
        applyFiltersAndSearch(_allSessions.value)
    }

    fun searchSessions(query: String?) {
        currentSearchQuery = query
        applyFiltersAndSearch(_allSessions.value)
    }

    private fun applyFiltersAndSearch(allSessions: List<TherapySession>) {
        var filteredSessions = allSessions

        // Aplicar filtro por estado
        if (currentFilter != null) {
            filteredSessions = filteredSessions.filter { session ->
                session.estado?.lowercase() == currentFilter?.lowercase()
            }
        }

        // Aplicar búsqueda
        if (!currentSearchQuery.isNullOrBlank()) {
            filteredSessions = filteredSessions.filter { session ->
                val query = currentSearchQuery!!.lowercase()
                session.paciente.nombresApellidos.lowercase().contains(query) ||
                session.terapeuta.nombresApellidos.lowercase().contains(query) ||
                session.descripcion?.lowercase()?.contains(query) == true ||
                session.ubicacion?.lowercase()?.contains(query) == true ||
                session.tipoSesion?.lowercase()?.contains(query) == true ||
                session.modalidad?.lowercase()?.contains(query) == true
            }
        }

        _sessions.value = filteredSessions
        android.util.Log.d("TherapyViewModel", "Filtered/searched sessions: ${filteredSessions.size} of ${allSessions.size}")
    }

    private fun applyCurrentFilter(allSessions: List<TherapySession>) {
        applyFiltersAndSearch(allSessions)
    }

    fun loadPaginatedSessions(
        page: Int = 1,
        limit: Int = 5,
        search: String? = null,
        estado: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _currentPage.value = page

            repository.getSessionsPaginated(page, limit, search, estado).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _paginatedSessions.value = result.data
                        android.util.Log.d("TherapyViewModel", "Loaded page $page with ${result.data.sessions.size} sessions")
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error loading paginated sessions: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadSession(sessionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getSession(sessionId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _currentSession.value = result.data
                        android.util.Log.d("TherapyViewModel", "Loaded session: ${result.data.id}")
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error loading session: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadSessionDetail(sessionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getSessionDetail(sessionId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _sessionDetail.value = result.data
                        android.util.Log.d("TherapyViewModel", "Loaded session detail: ${result.data.id}")
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error loading session detail: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun createSession(request: CreateTherapySessionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _createResult.value = false

            // Convert CreateTherapySessionRequest to CreateSessionRequest
            // Normalizar los valores para cumplir con las validaciones del backend
            val tipoSesionNormalizado = when(request.tipoSesion?.lowercase()) {
                "terapia individual", "individual" -> "individual"
                "terapia grupal", "grupal" -> "grupal"
                "evaluación", "seguimiento" -> "individual"
                else -> "individual"
            }

            val modalidadNormalizada = when(request.modalidad?.lowercase()) {
                "presencial" -> "presencial"
                "virtual" -> "virtual"
                "domicilio" -> "presencial"
                else -> "presencial"
            }

            // Asegurar formato de hora HH:mm (15:04) - SIN SEGUNDOS
            val horaInicio = when {
                request.horaInicio.matches(Regex("\\d{2}:\\d{2}")) -> request.horaInicio
                request.horaInicio.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) -> request.horaInicio.substring(0, 5)
                else -> "09:00"
            }

            val horaFin = when {
                request.horaFin.matches(Regex("\\d{2}:\\d{2}")) -> request.horaFin
                request.horaFin.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) -> request.horaFin.substring(0, 5)
                else -> "09:30" // Por defecto 30 minutos después
            }

            // Calcular duración basada en las horas si no viene especificada
            val duracion = if (request.duracion > 0) {
                // El backend solo acepta entre 30 y 45 minutos
                when {
                    request.duracion < 30 -> 30
                    request.duracion > 45 -> 45
                    else -> request.duracion
                }
            } else {
                try {
                    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val start = format.parse(horaInicio)
                    val end = format.parse(horaFin)

                    if (start != null && end != null) {
                        var diff = end.time - start.time
                        if (diff < 0) diff += 24 * 60 * 60 * 1000 // Día siguiente
                        val minutes = (diff / (1000 * 60)).toInt()

                        // Backend solo acepta entre 30 y 45 minutos
                        when {
                            minutes < 30 -> 30
                            minutes > 45 -> 45
                            else -> minutes
                        }
                    } else 30
                } catch (e: Exception) {
                    android.util.Log.e("TherapyViewModel", "Error calculating duration", e)
                    30
                }
            }

            val createRequest = CreateSessionRequest(
                paciente_id = request.pacienteId,
                terapeuta_id = request.terapeutaId,
                fecha_sesion = request.fechaSesion,
                hora_inicio = horaInicio,
                hora_fin = horaFin,
                ubicacion = request.ubicacion?.takeIf { it.length >= 5 } ?: "Consultorio principal",
                direccion = request.direccion?.takeIf { it.length >= 10 } ?: "Dirección del consultorio principal",
                descripcion = request.descripcion?.takeIf { it.length >= 20 }
                    ?: "Sesión terapéutica programada para el paciente",
                objetivos = request.objetivos?.takeIf { it.isNotEmpty() }
                    ?: listOf("Objetivo general de la sesión"),
                materiales = request.materiales?.takeIf { it.isNotEmpty() }
                    ?: listOf("Material estándar de terapia"),
                tipo_sesion = tipoSesionNormalizado,
                modalidad = modalidadNormalizada
            )

            // Log para debugging
            android.util.Log.d("TherapyViewModel", """
                Creating session with:
                - Patient ID: ${createRequest.paciente_id}
                - Date: ${createRequest.fecha_sesion}
                - Start Time: ${createRequest.hora_inicio}
                - End Time: ${createRequest.hora_fin}
                - Duration: $duracion minutes
                - Type: ${createRequest.tipo_sesion}
                - Modality: ${createRequest.modalidad}
                - Location: ${createRequest.ubicacion}
                - Address: ${createRequest.direccion}
                - Description: ${createRequest.descripcion}
                - Objectives: ${createRequest.objetivos.joinToString()}
                - Materials: ${createRequest.materiales.joinToString()}
            """.trimIndent())

            repository.createSession(createRequest).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _createResult.value = true
                        _successMessage.value = "Sesión creada exitosamente"
                        android.util.Log.d("TherapyViewModel", "Session created successfully: ${result.data.id}")
                        // Refrescar lista de sesiones
                        refreshCurrentView()
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        _createResult.value = false
                        android.util.Log.e("TherapyViewModel", "Error creating session: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        _createResult.value = false
                        android.util.Log.e("TherapyViewModel", "Network error creating session", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadPatients() {
        viewModelScope.launch {
            try {
                val patientsList = patientRepository.getAllPatients()
                _patients.value = patientsList
                android.util.Log.d("TherapyViewModel", "Loaded ${patientsList.size} patients")
            } catch (e: Exception) {
                android.util.Log.e("TherapyViewModel", "Error loading patients: ${e.message}")
                _error.value = e.message ?: "Error al cargar pacientes"
            }
        }
    }

    fun updateSession(sessionId: Int, request: UpdateSessionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.updateSession(sessionId, request).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _currentSession.value = result.data
                        _successMessage.value = "Sesión actualizada exitosamente"
                        android.util.Log.d("TherapyViewModel", "Session updated: ${result.data.id}")
                        // Refrescar lista de sesiones
                        refreshCurrentView()
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error updating session: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun rescheduleSession(sessionId: Int, request: RescheduleSessionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.rescheduleSession(sessionId, request).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _currentSession.value = result.data
                        _successMessage.value = "Sesión reprogramada exitosamente"
                        android.util.Log.d("TherapyViewModel", "Session rescheduled: ${result.data.id}")
                        // Refrescar lista de sesiones
                        refreshCurrentView()
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error rescheduling session: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.deleteSession(sessionId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _successMessage.value = "Sesión eliminada exitosamente"
                        android.util.Log.d("TherapyViewModel", "Session deleted: $sessionId")
                        // Refrescar lista de sesiones
                        refreshCurrentView()
                        // Limpiar sesión actual si era la que se eliminó
                        if (_currentSession.value?.id == sessionId) {
                            _currentSession.value = null
                        }
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error deleting session: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun exportSessionToPdf(sessionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.exportSessionToPdf(sessionId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _successMessage.value = "PDF exportado exitosamente"
                        android.util.Log.d("TherapyViewModel", "PDF exported for session: $sessionId")
                        // El PDF se descargaría o abriría según la implementación del repository
                    }
                    is ApiResult.Error -> {
                        _error.value = result.message
                        android.util.Log.e("TherapyViewModel", "Error exporting PDF: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        _error.value = "Error de conexión: ${result.exception.message}"
                        android.util.Log.e("TherapyViewModel", "Network error", result.exception)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadAvailableTherapists() {
        viewModelScope.launch {
            repository.getAvailableTherapists().collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _availableTherapists.value = result.data
                        android.util.Log.d("TherapyViewModel", "Loaded ${result.data.size} therapists")
                    }
                    is ApiResult.Error -> {
                        android.util.Log.e("TherapyViewModel", "Error loading therapists: ${result.message}")
                    }
                    is ApiResult.NetworkError -> {
                        android.util.Log.e("TherapyViewModel", "Network error loading therapists", result.exception)
                    }
                }
            }
        }
    }

    // Métodos para filtros y búsqueda
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedStatus(status: String?) {
        _selectedStatus.value = status
    }

    fun applyFilters() {
        val search = if (_searchQuery.value.isBlank()) null else _searchQuery.value
        val status = _selectedStatus.value
        loadPaginatedSessions(
            page = 1,
            search = search,
            estado = status
        )
    }

    fun nextPage() {
        val current = _paginatedSessions.value
        if (current?.has_next == true) {
            val search = if (_searchQuery.value.isBlank()) null else _searchQuery.value
            val status = _selectedStatus.value
            loadPaginatedSessions(
                page = _currentPage.value + 1,
                search = search,
                estado = status
            )
        }
    }

    fun previousPage() {
        if (_currentPage.value > 1) {
            val search = if (_searchQuery.value.isBlank()) null else _searchQuery.value
            val status = _selectedStatus.value
            loadPaginatedSessions(
                page = _currentPage.value - 1,
                search = search,
                estado = status
            )
        }
    }

    // Métodos para limpiar estados
    fun clearError() {
        _error.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun clearCurrentSession() {
        _currentSession.value = null
    }

    fun clearSessionDetail() {
        _sessionDetail.value = null
    }

    // Método privado para refrescar la vista actual
    private fun refreshCurrentView() {
        val search = if (_searchQuery.value.isBlank()) null else _searchQuery.value
        val status = _selectedStatus.value

        // Si estamos en vista paginada, refrescar
        if (_paginatedSessions.value != null) {
            loadPaginatedSessions(
                page = _currentPage.value,
                search = search,
                estado = status
            )
        }

        // Si estamos en vista de todas las sesiones, refrescar
        if (_sessions.value.isNotEmpty() && _paginatedSessions.value == null) {
            loadAllSessions()
        }
    }

    // Estados de sesiones comunes
    companion object {
        const val STATUS_PROGRAMADA = "programada"
        const val STATUS_EN_CURSO = "en_curso"
        const val STATUS_COMPLETADA = "completada"
        const val STATUS_CANCELADA = "cancelada"
        const val STATUS_REPROGRAMADA = "reprogramada"

        val ALL_STATUSES = listOf(
            STATUS_PROGRAMADA,
            STATUS_EN_CURSO,
            STATUS_COMPLETADA,
            STATUS_CANCELADA,
            STATUS_REPROGRAMADA
        )

        fun getStatusDisplayName(status: String): String {
            return when (status.lowercase()) {
                STATUS_PROGRAMADA -> "Programada"
                STATUS_EN_CURSO -> "En Curso"
                STATUS_COMPLETADA -> "Completada"
                STATUS_CANCELADA -> "Cancelada"
                STATUS_REPROGRAMADA -> "Reprogramada"
                else -> status.replaceFirstChar { it.uppercaseChar() }
            }
        }

        fun getStatusColor(status: String): Int {
            return when (status.lowercase()) {
                STATUS_PROGRAMADA -> android.graphics.Color.BLUE
                STATUS_EN_CURSO -> android.graphics.Color.parseColor("#FF9800") // Orange
                STATUS_COMPLETADA -> android.graphics.Color.GREEN
                STATUS_CANCELADA -> android.graphics.Color.RED
                STATUS_REPROGRAMADA -> android.graphics.Color.parseColor("#9C27B0") // Purple
                else -> android.graphics.Color.GRAY
            }
        }
    }
}