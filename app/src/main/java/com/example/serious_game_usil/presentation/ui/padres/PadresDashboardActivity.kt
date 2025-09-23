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
import com.example.serious_game_usil.presentation.ui.administrador.DashboardActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressViewModel
import com.example.serious_game_usil.presentation.ui.progress.ProgressDetailActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressAlertManager
import com.example.serious_game_usil.presentation.ui.patients.PatientDetailActivity
import com.example.serious_game_usil.utils.ImageUtils
import com.google.android.material.navigation.NavigationView
import com.bumptech.glide.Glide
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

        // Asegurar que el contenido principal esté visible
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
                // En caso de error, mantener el contenido visible pero sin datos de progreso
                binding.layoutContent.visibility = View.VISIBLE
                binding.layoutEmptyState.visibility = View.GONE
                binding.textProgresoPromedio.text = "0%"
                binding.textSesionesTotales.text = "0"
                binding.textMejorando.text = "0"
                binding.cardRecommendations.visibility = View.GONE
            }
        }

        progressViewModel.isLoading.observe(this) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observar pacientes del cuidador
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
    }

    private fun setupNavigation() {
        binding.cardMisHijos.setOnClickListener {
            val intent = Intent(this, MyPatientsActivity::class.java)
            startActivity(intent)
        }

        binding.cardRegistrarSesion.setOnClickListener {
            val intent = Intent(this, MySessionsActivity::class.java)
            startActivity(intent)
        }

        binding.cardProgreso.setOnClickListener {
            startActivity(Intent(this, ProgressDetailActivity::class.java))
        }

        binding.cardVideos.setOnClickListener {
            Toast.makeText(this, "Videos educativos - Próximamente", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, ProgressDetailActivity::class.java))
            }
            R.id.nav_progreso -> {
                startActivity(Intent(this, ProgressDetailActivity::class.java))
            }
            R.id.nav_emotion_analysis_padre -> {
                startActivity(Intent(this, com.example.serious_game_usil.presentation.ui.emotion.EmotionAnalysisActivity::class.java))
            }
            R.id.nav_nueva_sesion -> {
                Toast.makeText(this, "Nueva sesión - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_videos -> {
                Toast.makeText(this, "Videos educativos - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_reportes_padre -> {
                Toast.makeText(this, "Mis reportes - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_perfil_padre -> {
                Toast.makeText(this, "Mi perfil - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_contacto -> {
                val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.info.InfoActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout_padre -> {
                AuthManager.clearSession()
                RouteNavigator.navigateToLogin(this)
                finishAffinity()
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
        // Siempre mostrar el contenido principal
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
        binding.layoutContent.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        
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
    }

    private fun updatePatientsCount(count: Int) {
        binding.textTotalHijos.text = count.toString()
    }

    private fun updatePatientsSlider(patients: List<com.example.serious_game_usil.data.PatientListItem>) {
        if (patients.isEmpty()) {
            // Mostrar estado vacío
            binding.layoutNoPatientsSlider.visibility = View.VISIBLE
            binding.btnPreviousPatient.visibility = View.GONE
            binding.btnNextPatient.visibility = View.GONE
            binding.layoutPatientIndicators.visibility = View.GONE
            binding.textCurrentPatientName.text = "Sin pacientes asignados"
            binding.textCurrentPatientInfo.text = "Contacta a tu terapeuta"
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
            binding.textCurrentPatientName.text = currentPatient.nombresApellidos
            binding.textCurrentPatientInfo.text = "${currentPatient.edad} años • ${currentPatient.sexo}"

            // Cargar foto del paciente
            ImageUtils.loadUserPhoto(this, currentPatient.foto, binding.ivCurrentPatientPhoto)

            // Mostrar resumen de sesiones
            binding.layoutSessionsSummary.visibility = View.VISIBLE
            updatePatientSessionsSummary(currentPatient)

            // Actualizar indicadores
            if (patients.size > 1) {
                updatePatientIndicators()
            }
        } else {
            binding.layoutSessionsSummary.visibility = View.GONE
        }
    }

    private fun updatePatientSessionsSummary(patient: com.example.serious_game_usil.data.PatientListItem) {
        // Por ahora usar datos simulados hasta integrar con el backend de sesiones
        // TODO: Integrar con el servicio real de sesiones
        val totalSessions = (15..45).random() // Simular entre 15-45 sesiones
        val avgProgress = (65..95).random() // Simular progreso entre 65-95%
        val daysAgo = (1..30).random() // Última sesión hace X días

        binding.textTotalSessions.text = totalSessions.toString()
        binding.textAvgProgress.text = "${avgProgress}%"

        val lastSessionText = when {
            daysAgo == 1 -> "Ayer"
            daysAgo < 7 -> "${daysAgo}d"
            daysAgo < 30 -> "${daysAgo / 7}sem"
            else -> "1mes+"
        }
        binding.textLastSession.text = lastSessionText
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
}