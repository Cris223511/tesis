package com.example.serious_game_usil.presentation.ui.padres

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.databinding.ActivityMySessionsBinding
import com.example.serious_game_usil.presentation.ui.therapy.TherapySessionViewModel
import com.example.serious_game_usil.presentation.ui.therapy.RateTherapistActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            }
        )

        binding.recyclerViewSessions.apply {
            layoutManager = LinearLayoutManager(this@MySessionsActivity)
            adapter = sessionsAdapter
        }

        // Update adapter with any existing sessions data
        if (allSessions.isNotEmpty()) {
            sessionsAdapter.updateSessions(allSessions)
            updateEmptyState(allSessions, isSearching = false)
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
                    searchSessions(query)
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (newText.isNullOrBlank()) {
                        searchSessions(null)
                    } else if (newText.length >= 2) {
                        searchSessions(newText)
                    }
                    return true
                }
            })
        }

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mis Sesiones Terapéuticas"

        binding.swipeRefresh.setOnRefreshListener {
            loadSessions()
        }
    }

    private fun loadSessions() {
        binding.swipeRefresh.isRefreshing = true
        android.util.Log.d("MySessionsActivity", "Loading caregiver sessions...")
        viewModel.loadAllSessions()
    }

    private fun searchSessions(query: String?) {
        val filteredSessions = if (!query.isNullOrBlank()) {
            allSessions.filter { session ->
                session.tipoSesion?.contains(query, ignoreCase = true) == true ||
                session.terapeuta.nombresApellidos.contains(query, ignoreCase = true) ||
                session.paciente.nombresApellidos.contains(query, ignoreCase = true) ||
                session.ubicacion?.contains(query, ignoreCase = true) == true
            }
        } else {
            allSessions
        }

        if (::sessionsAdapter.isInitialized) {
            sessionsAdapter.updateSessions(filteredSessions)
            updateEmptyState(filteredSessions, isSearching = !query.isNullOrBlank())
        }
    }

    private fun getCurrentSearchQuery(): String {
        return binding.searchView.query.toString()
    }

    private fun updateEmptyState(sessions: List<TherapySession>, isSearching: Boolean) {
        if (sessions.isEmpty()) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewSessions.visibility = android.view.View.GONE

            if (isSearching) {
                binding.tvEmptyTitle.text = "Sin resultados"
                binding.tvEmptyMessage.text = "No se encontraron sesiones que coincidan con \"${getCurrentSearchQuery()}\""
            } else {
                binding.tvEmptyTitle.text = "Sin sesiones programadas"
                binding.tvEmptyMessage.text = "No tienes sesiones terapéuticas programadas. Contacta a tu terapeuta."
            }
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerViewSessions.visibility = android.view.View.VISIBLE
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
                android.util.Log.d("MySessionsActivity", "Received ${sessions.size} sessions for caregiver")
                allSessions = sessions
                if (::sessionsAdapter.isInitialized) {
                    sessionsAdapter.updateSessions(sessions)
                    updateEmptyState(sessions, isSearching = false)
                }
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
        Toast.makeText(this, "Analizando emociones para sesión #${session.id}...", Toast.LENGTH_SHORT).show()
        // TODO: Implement emotion analysis functionality
        android.util.Log.d("MySessionsActivity", "Analyze emotions requested for session ${session.id}")
    }

    private fun generateReport(session: TherapySession) {
        Toast.makeText(this, "Generando reporte para sesión #${session.id}...", Toast.LENGTH_SHORT).show()
        // TODO: Implement report generation functionality
        android.util.Log.d("MySessionsActivity", "Generate report requested for session ${session.id}")
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

    companion object {
        private const val REQUEST_CODE_RATE_THERAPIST = 1001
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadSessions() // Refresh when returning
    }
}