package com.example.serious_game_usil.presentation.ui.padres

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityPadresDashboardBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.progress.ProgressViewModel
import com.example.serious_game_usil.presentation.ui.progress.ProgressAlertManager
import com.example.serious_game_usil.presentation.ui.patients.PatientDetailActivity
import com.example.serious_game_usil.utils.ImageUtils
import com.google.android.material.navigation.NavigationView
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PadresDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityPadresDashboardBinding
    private lateinit var progressViewModel: ProgressViewModel
    private lateinit var dashboardViewModel: PadresDashboardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = ActivityPadresDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigationDrawer()
        setupUI()
        setupViewModel()
        setupNavigation()
        setupPatientsSlider()
        loadData()
    }

    private fun setupUI() {
        binding.userName.text = AuthManager.getNombresApellidos()
        updateDateTime()


        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }

        android.util.Log.d("PadresDashboard", "User roles: $userRoles")
        android.util.Log.d("PadresDashboard", "Is admin: $isAdmin")

        // Asegurar que el contenido principal esté visible (especialmente para admin)
        binding.layoutContent.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE

        // Cargar foto del usuario si está disponible
        val userPhoto = AuthManager.getFoto()
        android.util.Log.d("PadresDashboard", "UserPhoto from AuthManager: '$userPhoto'")
        ImageUtils.loadUserPhoto(this, userPhoto, binding.userAvatar)

        binding.logoutButton.setOnClickListener {
            AuthManager.clearSession()
            RouteNavigator.navigateToLogin(this)
            finishAffinity()
        }
    }

    private fun setupViewModel() {
        progressViewModel = ViewModelProvider(this)[ProgressViewModel::class.java]
        dashboardViewModel = ViewModelProvider(this)[PadresDashboardViewModel::class.java]

        progressViewModel.allChildrenProgress.observe(this) { progressList ->
            progressList?.let {
                updateDashboard(it)
                ProgressAlertManager.checkAndShowAlerts(this, it)
            }
        }

        progressViewModel.error.observe(this) { error ->
            error?.let {
                // Verificar si es administrador
                val userRoles = AuthManager.getUserRoles()
                val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }

                // Los administradores SIEMPRE ven el contenido, incluso con errores
                if (isAdmin) {
                    binding.layoutContent.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                } else {
                    // Los cuidadores normales también mantienen el contenido visible en caso de error
                    binding.layoutContent.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                }

                binding.textProgresoPromedio.text = "0%"
                binding.textSesionesTotales.text = "0"
                binding.textMejorando.text = "0"
                binding.cardRecommendations.visibility = View.GONE
            }
        }

        progressViewModel.isLoading.observe(this) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observar los últimos 3 pacientes del cuidador
        lifecycleScope.launch {
            dashboardViewModel.myPatients.collect { patients ->
                updatePatientsCount(patients.size)
                updatePatientsSlider(patients)
            }
        }

        // Inicializar valores por defecto
        binding.textTotalHijos.text = "0"

        lifecycleScope.launch {
            dashboardViewModel.currentPatientIndex.collect { index ->
                updateCurrentPatientDisplay()
            }
        }

        lifecycleScope.launch {
            dashboardViewModel.currentPatientStats.collect { stats ->
                updatePatientSessionsSummaryWithRealData(stats)
            }
        }
    }

    private fun setupNavigation() {
        binding.cardMisHijos.setOnClickListener {
            val intent = Intent(this, MyPatientsActivity::class.java)
            startActivity(intent)
        }



        binding.cardVideos.setOnClickListener {
            val intent = Intent(this, com.example.serious_game_usil.presentation.ui.videos.VideosEducativosActivity::class.java)
            startActivity(intent)
        }

        // Navegación inferior
        binding.navInicio.setOnClickListener {
            // Ya estamos en inicio, solo refrescar datos
            loadData()
        }

        binding.navPerfil.setOnClickListener {
            val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.profile.ProfileActivity::class.java)
            startActivity(intent)
        }

        binding.navContactar.setOnClickListener {
            val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.info.InfoActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupNavigationDrawer() {
        // Configurar el Navigation Drawer
        binding.navView.setNavigationItemSelectedListener(this)
        
        // Cargar foto del usuario en el header del drawer
        val headerView = binding.navView.getHeaderView(0)
        val drawerUserImage = headerView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imageView)
        
        val userPhoto = AuthManager.getFoto()
        android.util.Log.d("PadresDashboard", "UserPhoto for drawer: '$userPhoto'")
        ImageUtils.loadUserPhoto(this, userPhoto, drawerUserImage)
        
        // Configurar el botón hamburger
        binding.menuButton.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_padres_dashboard -> {
                // Ya estamos en el dashboard padres
            }
            R.id.nav_mis_hijos -> {
                startActivity(Intent(this, MyPatientsActivity::class.java))
            }
            R.id.nav_perfil_padre -> {
                startActivity(Intent(this, com.example.serious_game_usil.presentation.ui.administrador.profile.ProfileActivity::class.java))
            }
            R.id.nav_contacto -> {
                val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.info.InfoActivity::class.java)
                startActivity(intent)
            }
        }
        
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadData() {
        progressViewModel.loadAllChildrenProgress()
        dashboardViewModel.loadMyPatients()
    }

    private fun updateDashboard(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        // Verificar si es administrador
        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }

        // SIEMPRE mostrar contenido para administradores
        binding.layoutContent.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE

        if (progressList.isNotEmpty()) {
            // Actualizar estadísticas de progreso
            val avgProgress = progressList.map { getOverallScore(it) }.average()
            val totalSessions = progressList.sumOf { it.summary.totalSessions3M }
            val recentImprovement = progressList.count { it.summary.overallTrend == "improving" }

            binding.textProgresoPromedio.text = "${avgProgress.toInt()}%"
            binding.textSesionesTotales.text = totalSessions.toString()
            binding.textMejorando.text = recentImprovement.toString()

            // Actualizar recomendaciones
            updateRecommendations(progressList)
        } else {
            // Sin datos de progreso, usar valores por defecto
            binding.textProgresoPromedio.text = "0%"
            binding.textSesionesTotales.text = "0"
            binding.textMejorando.text = "0"
            binding.cardRecommendations.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        // Verificar si es administrador - los admin NUNCA deben ver empty state
        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }

        if (isAdmin) {
            // Los administradores SIEMPRE ven el contenido completo
            android.util.Log.d("PadresDashboard", "Admin detected - showing full content, no empty state")
            binding.layoutContent.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        } else {
            // Solo los cuidadores normales ven empty state
            binding.layoutContent.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        }

        binding.textTotalHijos.text = "0"
        binding.textProgresoPromedio.text = "0%"
        binding.textSesionesTotales.text = "0"
        binding.textMejorando.text = "0"
    }

    private fun getOverallScore(comparison: com.example.serious_game_usil.data.ThreeMonthComparison): Double {
        return comparison.currentMonth?.let { metrics ->
            (metrics.avgSocialInteraction + metrics.avgCommunication + 
             metrics.avgSensoryProcessing + metrics.avgAttentionFocus + 
             metrics.avgEmotionalRegulation) / 5.0
        } ?: 0.0
    }

    private fun updateRecommendations(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        val recommendations = mutableListOf<String>()
        
        // Generar recomendaciones inteligentes
        val lowProgressChildren = progressList.filter { getOverallScore(it) < 50 }
        if (lowProgressChildren.isNotEmpty()) {
            recommendations.add("${lowProgressChildren.size} niños necesitan más atención")
        }
        
        val improvingChildren = progressList.filter { it.summary.overallTrend == "improving" }
        if (improvingChildren.isNotEmpty()) {
            recommendations.add("¡${improvingChildren.size} niños están mejorando!")
        }

        if (progressList.any { it.summary.totalSessions3M < 12 }) {
            recommendations.add("Aumentar frecuencia de sesiones semanales")
        }

        // Mostrar recomendaciones
        if (recommendations.isNotEmpty()) {
            binding.textRecommendation.text = recommendations.joinToString(" • ")
            binding.cardRecommendations.visibility = View.VISIBLE
        } else {
            binding.cardRecommendations.visibility = View.GONE
        }
    }

    private fun updateDateTime() {
        val format = SimpleDateFormat("dd 'de' MMMM, HH:mm", Locale("es", "ES"))
        binding.textDateTime.text = format.format(Date())
    }

    override fun onResume() {
        super.onResume()
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
        } else {
            loadData() // Refrescar datos al volver
        }
    }

    private fun setupPatientsSlider() {
        binding.btnPreviousPatient.setOnClickListener {
            dashboardViewModel.previousPatient()
        }

        binding.btnNextPatient.setOnClickListener {
            dashboardViewModel.nextPatient()
        }

        // Click en el área del paciente actual para ir a su detalle
        binding.cardPatientsSlider.setOnClickListener {
            val currentPatient = dashboardViewModel.getCurrentPatient()
            currentPatient?.let { patient ->
                val intent = PatientDetailActivity.newIntent(this, patient.id)
                startActivity(intent)
            }
        }

        // Click en el botón Ver Sesiones para ir a las sesiones del paciente
        binding.btnViewPatientSessions.setOnClickListener {
            val currentPatient = dashboardViewModel.getCurrentPatient()
            currentPatient?.let { patient ->
                val intent = Intent(this, MySessionsActivity::class.java)
                intent.putExtra("patient_id", patient.id)
                intent.putExtra("patient_name", patient.nombresApellidos)
                startActivity(intent)
            }
        }
    }

    private fun updatePatientsCount(count: Int) {
        binding.textTotalHijos.text = count.toString()
    }

    private fun updatePatientsSlider(patients: List<com.example.serious_game_usil.data.PatientListItem>) {
        val isAdmin = AuthManager.isAdmin()

        if (patients.isEmpty() && !isAdmin) {

            binding.layoutNoPatientsSlider.visibility = View.VISIBLE
            binding.btnPreviousPatient.visibility = View.GONE
            binding.btnNextPatient.visibility = View.GONE
            binding.layoutPatientIndicators.visibility = View.GONE
            binding.textCurrentPatientName.text = "Sin pacientes recientes"
            binding.textCurrentPatientInfo.text = "Contacta a tu terapeuta"
        } else if (patients.isEmpty() && isAdmin) {
            // Para administradores sin pacientes en la base de datos
            binding.layoutNoPatientsSlider.visibility = View.GONE
            binding.btnPreviousPatient.visibility = View.GONE
            binding.btnNextPatient.visibility = View.GONE
            binding.layoutPatientIndicators.visibility = View.GONE
            binding.textCurrentPatientName.text = "Vista de Administrador"
            binding.textCurrentPatientInfo.text = "No hay pacientes en el sistema"
            // Asegurar que el contenido sigue visible
            binding.layoutContent.visibility = View.VISIBLE
        } else {
            // Mostrar slider
            binding.layoutNoPatientsSlider.visibility = View.GONE
            binding.btnPreviousPatient.visibility = if (patients.size > 1) View.VISIBLE else View.INVISIBLE
            binding.btnNextPatient.visibility = if (patients.size > 1) View.VISIBLE else View.INVISIBLE

            // Crear indicadores de posición si hay más de 1 paciente
            if (patients.size > 1) {
                binding.layoutPatientIndicators.visibility = View.VISIBLE
                createPatientIndicators(patients.size)
            } else {
                binding.layoutPatientIndicators.visibility = View.GONE
            }

            updateCurrentPatientDisplay()
        }
    }

    private fun updateCurrentPatientDisplay() {
        val currentPatient = dashboardViewModel.getCurrentPatient()
        val patients = dashboardViewModel.myPatients.value

        if (currentPatient != null) {
            android.util.Log.d("PadresDashboard", "Current patient: ${currentPatient.nombresApellidos}")
            android.util.Log.d("PadresDashboard", "Patient age: ${currentPatient.edad}")
            android.util.Log.d("PadresDashboard", "Patient sex: ${currentPatient.sexo}")

            binding.textCurrentPatientName.text = currentPatient.nombresApellidos
            binding.textCurrentPatientInfo.text = "${currentPatient.edad} años • ${currentPatient.sexo}"

            // Cargar foto del paciente con validación mejorada
            loadPatientPhoto(currentPatient.fotoMovil, binding.ivCurrentPatientPhoto)

            // Mostrar botón Ver Sesiones
            binding.btnViewPatientSessions.visibility = View.VISIBLE
            binding.btnViewPatientSessions.text = "Ver Sesiones de ${currentPatient.nombresApellidos.split(" ")[0]}"

            // Mostrar resumen de sesiones
            binding.layoutSessionsSummary.visibility = View.VISIBLE
            updatePatientSessionsSummary(currentPatient)

            // Actualizar indicadores
            if (patients.size > 1) {
                updatePatientIndicators()
            }
        } else {
            binding.layoutSessionsSummary.visibility = View.GONE
            binding.btnViewPatientSessions.visibility = View.GONE
        }
    }

    private fun updatePatientSessionsSummary(patient: com.example.serious_game_usil.data.PatientListItem) {
        // Cargar estadísticas reales del paciente
        dashboardViewModel.loadCurrentPatientStats()
    }

    private fun updatePatientSessionsSummaryWithRealData(stats: com.example.serious_game_usil.data.PatientStatsResponse?) {
        if (stats != null) {
            android.util.Log.d("PadresDashboard", "Updating UI with real stats: ${stats}")

            // Actualizar cards superiores con datos reales
            binding.textSesionesTotales.text = stats.totalSessions.toString()
            binding.textProgresoPromedio.text = "${stats.progressPercentage}%"

            // Para "Mejorando" podemos usar las sesiones completadas como indicador de mejora
            binding.textMejorando.text = stats.completedSessions.toString()

            // Actualizar resumen del paciente actual
            binding.textTotalSessions.text = stats.totalSessions.toString()
            binding.textAvgProgress.text = "${stats.progressPercentage}%"

            val lastSessionText = when {
                stats.daysSinceLastSession == 0 -> "Hoy"
                stats.daysSinceLastSession == 1 -> "Ayer"
                stats.daysSinceLastSession < 7 -> "${stats.daysSinceLastSession}d"
                stats.daysSinceLastSession < 30 -> "${stats.daysSinceLastSession / 7}sem"
                else -> "1mes+"
            }
            binding.textLastSession.text = lastSessionText
        } else {
            android.util.Log.d("PadresDashboard", "No stats available, using default values")
            // Usar valores por defecto si no hay estadísticas
            binding.textSesionesTotales.text = "0"
            binding.textProgresoPromedio.text = "0%"
            binding.textMejorando.text = "0"
            binding.textTotalSessions.text = "0"
            binding.textAvgProgress.text = "0%"
            binding.textLastSession.text = "N/A"
        }
    }

    private fun createPatientIndicators(count: Int) {
        binding.layoutPatientIndicators.removeAllViews()

        for (i in 0 until count) {
            val indicator = View(this)
            val size = (8 * resources.displayMetrics.density).toInt()
            val margin = (4 * resources.displayMetrics.density).toInt()

            val layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
            layoutParams.setMargins(margin, 0, margin, 0)
            indicator.layoutParams = layoutParams
            indicator.setBackgroundResource(R.drawable.indicator_inactive)

            binding.layoutPatientIndicators.addView(indicator)
        }

        updatePatientIndicators()
    }

    private fun updatePatientIndicators() {
        val currentIndex = dashboardViewModel.currentPatientIndex.value
        for (i in 0 until binding.layoutPatientIndicators.childCount) {
            val indicator = binding.layoutPatientIndicators.getChildAt(i)
            if (i == currentIndex) {
                indicator.setBackgroundResource(R.drawable.indicator_active)
            } else {
                indicator.setBackgroundResource(R.drawable.indicator_inactive)
            }
        }
    }


    private fun loadPatientPhoto(photoBase64: String?, imageView: com.google.android.material.imageview.ShapeableImageView) {
        if (!photoBase64.isNullOrBlank() && photoBase64.length > 20) {
            try {
                val decodedBytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    // Usar icono específico para pacientes
                    imageView.setImageResource(R.drawable.ic_patient_child)
                }
            } catch (e: Exception) {
                android.util.Log.e("PadresDashboard", "Error decoding patient photo: ${e.message}")
                imageView.setImageResource(R.drawable.ic_patient_child)
            }
        } else {
            // Placeholder para pacientes sin foto
            imageView.setImageResource(R.drawable.ic_patient_child)
        }
    }
}
