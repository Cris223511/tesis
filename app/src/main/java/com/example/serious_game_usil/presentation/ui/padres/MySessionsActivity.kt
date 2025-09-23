package com.example.serious_game_usil.presentation.ui.padres

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.TherapySession
import com.example.serious_game_usil.databinding.ActivityMySessionsBinding
import com.example.serious_game_usil.utils.MySessionsAdapter
import java.text.SimpleDateFormat
import java.util.*

class MySessionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMySessionsBinding
    private lateinit var sessionsAdapter: MySessionsAdapter
    private var allSessions = listOf<TherapySession>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadSessions()
    }

    private fun setupUI() {
        // Setup RecyclerView
        sessionsAdapter = MySessionsAdapter { session ->
            val intent = Intent(this, SessionDetailActivity::class.java)
            intent.putExtra("session_id", session.id)
            startActivity(intent)
        }

        binding.recyclerViewSessions.apply {
            layoutManager = LinearLayoutManager(this@MySessionsActivity)
            adapter = sessionsAdapter
        }

        // Setup search
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchSessions(query)
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

        // Simular datos de sesiones hasta integrar con backend real
        // TODO: Integrar con el servicio real de sesiones
        val simulatedSessions = generateSimulatedSessions()

        allSessions = simulatedSessions
        sessionsAdapter.updateSessions(simulatedSessions)
        updateEmptyState(simulatedSessions, isSearching = false)

        binding.swipeRefresh.isRefreshing = false
    }

    private fun searchSessions(query: String?) {
        val filteredSessions = if (!query.isNullOrBlank()) {
            allSessions.filter { session ->
                session.nombreSesion.contains(query, ignoreCase = true) ||
                session.terapeutaNombre.contains(query, ignoreCase = true) ||
                session.pacienteNombre.contains(query, ignoreCase = true) ||
                session.ubicacion?.contains(query, ignoreCase = true) == true
            }
        } else {
            allSessions
        }

        sessionsAdapter.updateSessions(filteredSessions)
        updateEmptyState(filteredSessions, isSearching = !query.isNullOrBlank())
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

    private fun generateSimulatedSessions(): List<TherapySession> {
        val sessions = mutableListOf<TherapySession>()
        val calendar = Calendar.getInstance()

        // Generar sesiones para las próximas 2 semanas
        for (i in 1..10) {
            calendar.add(Calendar.DAY_OF_YEAR, if (i <= 5) 1 else 2)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            // Alternar horas (9:00 AM, 2:00 PM, 4:00 PM)
            val hours = listOf(9, 14, 16)
            calendar.set(Calendar.HOUR_OF_DAY, hours[i % 3])
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            val session = TherapySession(
                id = i,
                nombreSesion = listOf(
                    "Terapia de Comunicación Social",
                    "Sesión de Habilidades Motoras",
                    "Terapia Sensorial",
                    "Desarrollo del Lenguaje",
                    "Terapia Ocupacional"
                )[i % 5],
                fechaHora = dateFormat.format(calendar.time),
                terapeutaNombre = listOf(
                    "Dra. María González",
                    "Dr. Carlos Mendoza",
                    "Lic. Ana Rojas"
                )[i % 3],
                pacienteNombre = "Mi hijo ${listOf("Diego", "Sofía", "Mateo")[i % 3]}",
                ubicacion = listOf(
                    "Consultorio 101 - Centro Terapéutico",
                    "Sala de Terapia A - USIL",
                    "Consultorio Virtual - Zoom",
                    "Domicilio - Visita a casa"
                )[i % 4],
                descripcion = "Sesión de terapia personalizada enfocada en el desarrollo de habilidades específicas",
                estado = if (i <= 2) "completada" else if (i <= 7) "programada" else "pendiente",
                duracionMinutos = 60,
                notas = null,
                createdAt = dateFormat.format(Date()),
                updatedAt = dateFormat.format(Date())
            )
            sessions.add(session)
        }

        return sessions.sortedBy { it.fechaHora }
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