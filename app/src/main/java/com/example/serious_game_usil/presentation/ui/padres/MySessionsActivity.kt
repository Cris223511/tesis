package com.example.serious_game_usil.presentation.ui.padres

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.databinding.ActivityMySessionsBinding
import com.example.serious_game_usil.presentation.ui.emotion.EmotionAnalysisActivity
import com.example.serious_game_usil.presentation.ui.therapy.TherapySessionViewModel
import com.example.serious_game_usil.presentation.ui.therapy.RateTherapistActivity
import com.example.serious_game_usil.presentation.ui.terapeuta.GenerateSessionReportActivity
import com.example.serious_game_usil.utils.MySessionsAdapter
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MySessionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMySessionsBinding
    private lateinit var sessionsAdapter: MySessionsAdapter
    private lateinit var viewModel: TherapySessionViewModel
    private var allSessions = listOf<TherapySession>()
    private var filteredSessions = listOf<TherapySession>()
    private var specificPatientId: Int? = null
    private var specificPatientName: String? = null
    private var currentPage = 0

    companion object {
        private const val REQUEST_CODE_RATE_THERAPIST = 1001
        private const val PAGE_SIZE = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        specificPatientId = intent.getIntExtra("patient_id", -1).takeIf { it != -1 }
        specificPatientName = intent.getStringExtra("patient_name")

        // Verificar autenticación
        AuthManager.init(this)
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        // Configurar token
        AuthManager.getAccessToken()?.let { token ->
            com.example.serious_game_usil.network.RetrofitClient.setAuthToken(token)
        } ?: run {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = ActivityMySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupViewModel()
        loadSessions()
    }

    private fun setupUI() {
        // Setup RecyclerView
        sessionsAdapter = MySessionsAdapter(
            onItemClick = { session ->
                val intent = Intent(this, SessionDetailActivity::class.java)
                intent.putExtra("session_id", session.id)
                startActivity(intent)
            },
            onAnalyzeEmotionsClick = { session ->
                analyzeEmotions(session)
            },
            onGenerateReportClick = { session ->
                generateReport(session)
            },
            onRateTherapistClick = { session ->
                rateTherapist(session)
            },
            canAnalyzeEmotions = { session ->
                getEmotionAnalysisAvailability(session).isAvailable
            }
        )

        binding.recyclerViewSessions.apply {
            layoutManager = LinearLayoutManager(this@MySessionsActivity)
            adapter = sessionsAdapter
        }

        binding.searchView.queryHint = if (specificPatientName != null) {
            "Buscar sesiones o terapeuta de $specificPatientName"
        } else {
            "Buscar sesiones, terapeuta o paciente"
        }

        binding.btnPreviousPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderCurrentPage(animate = true)
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPage < getTotalPages(filteredSessions) - 1) {
                currentPage++
                renderCurrentPage(animate = true)
            }
        }

        // Setup search
        binding.searchView.apply {
            isIconified = false
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocusFromTouch()
            clearFocus()

            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    applySessionFilter(query)
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (newText.isNullOrBlank()) {
                        applySessionFilter(null)
                    } else if (newText.length >= 2) {
                        applySessionFilter(newText)
                    }
                    return true
                }
            })
        }

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (specificPatientName != null) {
            "Sesiones de ${specificPatientName}"
        } else {
            "Mis Sesiones Terapéuticas"
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadSessions()
        }
    }

    private fun loadSessions() {
        binding.swipeRefresh.isRefreshing = true
        android.util.Log.d("MySessionsActivity", "Loading caregiver sessions...")
        viewModel.loadAllSessions()
    }

    private fun applySessionFilter(query: String?) {
        filteredSessions = if (!query.isNullOrBlank()) {
            allSessions.filter { session ->
                session.tipoSesion?.contains(query, ignoreCase = true) == true ||
                session.terapeuta.nombresApellidos.contains(query, ignoreCase = true) ||
                session.paciente.nombresApellidos.contains(query, ignoreCase = true) ||
                session.ubicacion?.contains(query, ignoreCase = true) == true
            }
        } else {
            allSessions
        }

        currentPage = 0
        renderCurrentPage(animate = false, isSearching = !query.isNullOrBlank())
    }

    private fun getCurrentSearchQuery(): String {
        return binding.searchView.query.toString()
    }

    private fun updateEmptyState(sessions: List<TherapySession>, isSearching: Boolean) {
        if (sessions.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewSessions.visibility = View.GONE
            binding.paginationCard.visibility = View.GONE

            if (isSearching) {
                binding.tvEmptyTitle.text = "Sin resultados"
                binding.tvEmptyMessage.text = "No se encontraron sesiones que coincidan con \"${getCurrentSearchQuery()}\""
            } else if (specificPatientName != null) {
                binding.tvEmptyTitle.text = "Sin reportes para ${specificPatientName}"
                binding.tvEmptyMessage.text = "Cuando este paciente tenga sesiones, podrás generar su reporte individual desde aquí."
            } else {
                binding.tvEmptyTitle.text = "Sin sesiones programadas"
                binding.tvEmptyMessage.text = "No tienes sesiones terapéuticas programadas. Contacta a tu terapeuta."
            }
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewSessions.visibility = View.VISIBLE
            binding.paginationCard.visibility = if (getTotalPages(sessions) > 1) View.VISIBLE else View.GONE
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TherapySessionViewModel::class.java]

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@MySessionsActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sessions.collect { sessions ->
                val scopedSessions = sessions.filterByPatient()
                android.util.Log.d("MySessionsActivity", "Received ${scopedSessions.size} sessions for caregiver")
                allSessions = scopedSessions
                filteredSessions = scopedSessions
                currentPage = 0
                renderCurrentPage(animate = false)
                binding.swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launch {
            viewModel.successMessage.collect { message ->
                message?.let {
                    Toast.makeText(this@MySessionsActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                }
            }
        }
    }

    private fun analyzeEmotions(session: TherapySession) {
        val availability = getEmotionAnalysisAvailability(session)
        if (!availability.isAvailable) {
            Toast.makeText(this, availability.message, Toast.LENGTH_LONG).show()
            return
        }

        startActivity(Intent(this, EmotionAnalysisActivity::class.java).apply {
            putExtra("session_id", session.id)
            putExtra("patient_id", session.pacienteId)
        })
    }

    private fun generateReport(session: TherapySession) {
        startActivity(GenerateSessionReportActivity.newIntent(this, session.id))
    }

    private fun rateTherapist(session: TherapySession) {
        // Obtener el ID del cuidador actual
        val currentUserId = AuthManager.getUserId()
        val caregiverName = AuthManager.getNombresApellidos()

        if (currentUserId == -1) {
            Toast.makeText(this, "Error: No se pudo obtener información del usuario", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = RateTherapistActivity.newIntent(
            context = this,
            sessionId = session.id,
            therapistId = session.terapeutaId,
            therapistName = session.terapeuta.nombresApellidos,
            patientName = session.paciente.nombresApellidos,
            patientId = session.pacienteId,
            caregiverId = currentUserId
        )
        startActivityForResult(intent, REQUEST_CODE_RATE_THERAPIST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_RATE_THERAPIST && resultCode == RESULT_OK) {
            // Recargar sesiones después de calificar
            Toast.makeText(this, "Calificación enviada exitosamente", Toast.LENGTH_SHORT).show()
            loadSessions()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadSessions() // Refresh when returning
    }

    private fun List<TherapySession>.filterByPatient(): List<TherapySession> {
        val patientId = specificPatientId ?: return this
        return filter { it.pacienteId == patientId }
    }

    private fun renderCurrentPage(animate: Boolean, isSearching: Boolean = false) {
        if (!::sessionsAdapter.isInitialized) return

        updateEmptyState(filteredSessions, isSearching)
        if (filteredSessions.isEmpty()) return

        val totalPages = getTotalPages(filteredSessions)
        currentPage = currentPage.coerceIn(0, maxOf(totalPages - 1, 0))
        val fromIndex = currentPage * PAGE_SIZE
        val toIndex = minOf(fromIndex + PAGE_SIZE, filteredSessions.size)
        val pageSessions = filteredSessions.subList(fromIndex, toIndex)

        val updateList = {
            sessionsAdapter.updateSessions(pageSessions)
            binding.tvPageIndicator.text = "Pagina ${currentPage + 1} de $totalPages"
            binding.tvPaginationSummary.text = "Mostrando ${fromIndex + 1}-$toIndex de ${filteredSessions.size}"
            binding.btnPreviousPage.isEnabled = currentPage > 0
            binding.btnNextPage.isEnabled = currentPage < totalPages - 1
            binding.recyclerViewSessions.scrollToPosition(0)
        }

        if (animate) {
            binding.recyclerViewSessions.animate()
                .alpha(0f)
                .translationY(18f)
                .setDuration(140)
                .withEndAction {
                    updateList()
                    binding.recyclerViewSessions.alpha = 0f
                    binding.recyclerViewSessions.translationY = -18f
                    binding.recyclerViewSessions.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .start()
                }
                .start()
        } else {
            binding.recyclerViewSessions.alpha = 1f
            binding.recyclerViewSessions.translationY = 0f
            updateList()
        }
    }

    private fun getTotalPages(items: List<TherapySession>): Int {
        if (items.isEmpty()) return 0
        return ((items.size - 1) / PAGE_SIZE) + 1
    }

    private fun getEmotionAnalysisAvailability(session: TherapySession): EmotionAnalysisAvailability {
        val normalizedStatus = session.estado.trim().lowercase(Locale.ROOT)
        if (normalizedStatus == "cancelada") {
            return EmotionAnalysisAvailability(
                isAvailable = false,
                message = "Esta sesión fue cancelada y ya no permite análisis emocional."
            )
        }

        if (normalizedStatus == "completada" || normalizedStatus == "finalizada") {
            return EmotionAnalysisAvailability(
                isAvailable = false,
                message = "La sesión ya finalizó. El análisis emocional solo está disponible mientras la sesión siga vigente."
            )
        }

        val sessionDate = parseSessionDate(session.fechaSesion)
            ?: return EmotionAnalysisAvailability(false, "No se pudo validar la fecha de esta sesión.")
        val startTime = parseSessionTime(session.horaInicio)
            ?: return EmotionAnalysisAvailability(false, "No se pudo validar la hora de inicio de esta sesión.")
        val endTime = parseSessionTime(session.horaFin)
            ?: return EmotionAnalysisAvailability(false, "No se pudo validar la hora de fin de esta sesión.")

        val now = Calendar.getInstance()
        val startDateTime = Calendar.getInstance().apply {
            time = sessionDate
            set(Calendar.HOUR_OF_DAY, startTime.first)
            set(Calendar.MINUTE, startTime.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endDateTime = Calendar.getInstance().apply {
            time = sessionDate
            set(Calendar.HOUR_OF_DAY, endTime.first)
            set(Calendar.MINUTE, endTime.second)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        if (now.before(startDateTime)) {
            return EmotionAnalysisAvailability(
                isAvailable = false,
                message = "El análisis emocional estará disponible cuando inicie la sesión programada."
            )
        }

        if (now.after(endDateTime)) {
            return EmotionAnalysisAvailability(
                isAvailable = false,
                message = "La sesión ya terminó por horario. El análisis emocional solo puede hacerse dentro del tiempo de la sesión."
            )
        }

        return EmotionAnalysisAvailability(true)
    }

    private fun parseSessionDate(rawDate: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        )
        return formats.firstNotNullOfOrNull { format ->
            runCatching {
                format.isLenient = false
                format.parse(rawDate)
            }.getOrNull()
        }
    }

    private fun parseSessionTime(rawTime: String): Pair<Int, Int>? {
        val cleaned = rawTime.trim().take(5)
        val parts = cleaned.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour to minute
    }

    private data class EmotionAnalysisAvailability(
        val isAvailable: Boolean,
        val message: String? = null
    )
}
