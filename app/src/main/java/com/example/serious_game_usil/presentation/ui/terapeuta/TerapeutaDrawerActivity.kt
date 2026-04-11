package com.example.serious_game_usil.presentation.ui.terapeuta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.databinding.DashboardTerapeutaBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.utils.ImageUtils
import com.example.serious_game_usil.network.RetrofitClient
import com.seriousgame.app.navigation.RouteNavigator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TerapeutaDrawerActivity : AppCompatActivity() {

    private lateinit var binding: DashboardTerapeutaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = DashboardTerapeutaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupToolbar()
        setupQuickActions()
        loadDashboardStats()
    }

    private fun setupViews() {
        // Usar el nombre del usuario autenticado
        binding.userName.text = AuthManager.getNombresApellidos()
        binding.cardManagePatients.visibility = View.GONE

        // Cargar foto del usuario si está disponible
        val userPhoto = AuthManager.getFoto()
        ImageUtils.loadUserPhoto(this, userPhoto, binding.userAvatar)

        // Configurar botón de logout
        binding.logoutButton.setOnClickListener {
            AuthManager.clearSession()
            RouteNavigator.navigateToLogin(this)
            finishAffinity()
        }
    }

    private fun setupToolbar() {
        // Configurar toolbar simple sin navigation drawer
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Dashboard Terapeuta"
    }

    private fun setupQuickActions() {
        // Botón de gestionar sesiones
        binding.cardSessionsManagement.setOnClickListener {
            val intent = Intent(this, com.example.serious_game_usil.presentation.ui.therapy.SimpleTherapySessionsActivity::class.java)
            startActivity(intent)
        }

        // Botón de reportes
        binding.cardReports.setOnClickListener {
            val intent = Intent(this, TherapyReportsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // Re-verificar autenticación cuando la activity vuelve a estar activa
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
        }
    }

    private fun loadDashboardStats() {
        // Configurar token de autenticación
        AuthManager.getAccessToken()?.let { token ->
            RetrofitClient.setAuthToken(token)
        }

        // Obtener roles del usuario para determinar qué datos mostrar
        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any {
            it.equals("admin", ignoreCase = true) ||
            it.equals("administrador", ignoreCase = true) ||
            it.equals("AD", ignoreCase = true)
        }

        lifecycleScope.launch {
            try {
                if (isAdmin) {
                    loadAdminStats()
                } else {
                    loadTherapistStats()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TerapeutaDrawerActivity,
                    "Error al cargar estadísticas: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadAdminStats() {
        try {
            val apiService = RetrofitClient.getApiService()

            // Para admin: obtener estadísticas globales del sistema
            // Total de pacientes
            val patientsResponse = apiService.getPatients(page = 1, limit = 1000)
            val totalPatients = if (patientsResponse.isSuccessful) {
                patientsResponse.body()?.patients?.size ?: 0
            } else 0

            // Total de sesiones programadas
            val sessionsResponse = apiService.getSessionsPaginated(page = 1, limit = 1000)
            val totalSessions = if (sessionsResponse.isSuccessful) {
                sessionsResponse.body()?.data?.sessions?.size ?: 0
            } else 0

            // Total de terapeutas (usuarios con rol terapeuta)
            val usersResponse = apiService.getUsers(page = 1, perPage = 1000)
            val totalTherapists = if (usersResponse.isSuccessful) {
                val users = usersResponse.body()?.users
                android.util.Log.d("TerapeutaDrawer", "Total usuarios encontrados: ${users?.size}")

                val therapists = users?.count { user ->
                    val userRoles = user.roles
                    android.util.Log.d("TerapeutaDrawer", "Usuario: ${user.nombresApellidos}, Roles: $userRoles")

                    // Buscar múltiples variaciones de rol terapeuta
                    val isTherapist = userRoles?.any { role ->
                        val roleStr = role.toString().trim()
                        roleStr.equals("TR", ignoreCase = true) ||
                        roleStr.equals("terapeuta", ignoreCase = true) ||
                        roleStr.equals("therapist", ignoreCase = true) ||
                        roleStr.contains("terapeu", ignoreCase = true)
                    } == true

                    android.util.Log.d("TerapeutaDrawer", "Es terapeuta: $isTherapist")
                    isTherapist
                } ?: 0

                android.util.Log.d("TerapeutaDrawer", "Total terapeutas encontrados: $therapists")
                therapists
            } else {
                android.util.Log.e("TerapeutaDrawer", "Error al obtener usuarios: ${usersResponse.code()}")
                0
            }

            // Actualizar UI en el hilo principal
            runOnUiThread {
                updateDashboardUI(
                    patients = totalPatients,
                    sessions = totalSessions,
                    progress = calculateSystemProgress(totalSessions),
                    isAdmin = true,
                    therapists = totalTherapists
                )
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@TerapeutaDrawerActivity,
                    "Error al cargar estadísticas de admin: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadTherapistStats() {
        try {
            val apiService = RetrofitClient.getApiService()
            val currentUserId = AuthManager.getUserId()

            // Para terapeuta: obtener solo SUS datos
            // Por simplicidad, usamos valores estáticos para terapeuta individual
            // En un futuro se pueden crear endpoints específicos para estadísticas por terapeuta

            // Sus pacientes asignados (estimación basada en nombre del terapeuta)
            val currentUserName = AuthManager.getNombresApellidos()
            val patientsResponse = apiService.getPatients(page = 1, limit = 1000)
            val myPatients = if (patientsResponse.isSuccessful) {
                patientsResponse.body()?.patients?.count { patient ->
                    // Filtrar por nombre del terapeuta
                    patient.terapeutaNombre.contains(currentUserName, ignoreCase = true)
                } ?: 0
            } else 0

            // Sus sesiones (estimación)
            val sessionsResponse = apiService.getSessionsPaginated(page = 1, limit = 1000)
            val mySessions = if (sessionsResponse.isSuccessful) {
                sessionsResponse.body()?.data?.sessions?.count { session ->
                    // Filtrar sesiones donde este usuario es el terapeuta por nombre
                    session.terapeuta.nombresApellidos.contains(currentUserName, ignoreCase = true)
                } ?: 0
            } else 0

            // Actualizar UI en el hilo principal
            runOnUiThread {
                updateDashboardUI(
                    patients = myPatients,
                    sessions = mySessions,
                    progress = calculateTherapistProgress(mySessions),
                    isAdmin = false
                )
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@TerapeutaDrawerActivity,
                    "Error al cargar estadísticas del terapeuta: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDashboardUI(
        patients: Int,
        sessions: Int,
        progress: Int,
        isAdmin: Boolean,
        therapists: Int = 0
    ) {
        // Actualizar las estadísticas principales (solo números, sin labels)
        if (isAdmin) {
            // Para admin: mostrar estadísticas globales
            binding.textTotalPatients.text = patients.toString()
            binding.textWeeklySessions.text = sessions.toString()
            binding.textAvgProgress.text = therapists.toString()
        } else {
            // Para terapeuta: mostrar sus propias estadísticas
            binding.textTotalPatients.text = patients.toString()
            binding.textWeeklySessions.text = sessions.toString()
            binding.textAvgProgress.text = progress.toString()
        }
    }

    private fun calculateSystemProgress(totalSessions: Int): Int {
        // Lógica mejorada: progreso basado en actividad del sistema
        return when {
            totalSessions >= 50 -> 95  // Sistema muy activo
            totalSessions >= 20 -> 75  // Sistema activo
            totalSessions >= 10 -> 50  // Sistema moderado
            totalSessions >= 5 -> 25   // Sistema en inicio
            totalSessions > 0 -> 10    // Algo de actividad
            else -> 0                  // Sin actividad
        }
    }

    private fun calculateTherapistProgress(mySessions: Int): Int {
        // Lógica simple para progreso individual del terapeuta
        return if (mySessions > 0) {
            minOf(100, (mySessions * 15)) // Cada sesión cuenta más para progreso individual
        } else 0
    }
}
