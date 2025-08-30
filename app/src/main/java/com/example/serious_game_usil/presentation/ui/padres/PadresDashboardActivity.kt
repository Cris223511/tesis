package com.example.serious_game_usil.presentation.ui.padres

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityPadresDashboardBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.administrador.DashboardActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressViewModel
import com.example.serious_game_usil.presentation.ui.progress.ProgressDetailActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressAlertManager
import com.google.android.material.navigation.NavigationView
import com.bumptech.glide.Glide
import com.example.serious_game_usil.utils.ImageUtils
import com.seriousgame.app.navigation.RouteNavigator
import java.text.SimpleDateFormat
import java.util.*

class PadresDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityPadresDashboardBinding
    private lateinit var progressViewModel: ProgressViewModel

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
        loadData()
    }

    private fun setupUI() {
        binding.userName.text = AuthManager.getNombresApellidos()
        updateDateTime()
        
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
        
        progressViewModel.allChildrenProgress.observe(this) { progressList ->
            progressList?.let {
                updateDashboard(it)
                ProgressAlertManager.checkAndShowAlerts(this, it)
            }
        }

        progressViewModel.error.observe(this) { error ->
            error?.let {
                showEmptyState()
            }
        }

        progressViewModel.isLoading.observe(this) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupNavigation() {
        binding.cardMisHijos.setOnClickListener {
            startActivity(Intent(this, ProgressDetailActivity::class.java))
        }

        binding.cardRegistrarSesion.setOnClickListener {
            Toast.makeText(this, "Registrar nueva sesión - Próximamente", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Ver perfil - Próximamente", Toast.LENGTH_SHORT).show()
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
    }

    private fun updateDashboard(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        if (progressList.isEmpty()) {
            showEmptyState()
            return
        }

        binding.layoutContent.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE

        // Actualizar estadísticas principales
        val totalChildren = progressList.size
        val avgProgress = progressList.map { getOverallScore(it) }.average()
        val totalSessions = progressList.sumOf { it.summary.totalSessions3M }
        val recentImprovement = progressList.count { it.summary.overallTrend == "improving" }

        binding.textTotalHijos.text = totalChildren.toString()
        binding.textProgresoPromedio.text = "${avgProgress.toInt()}%"
        binding.textSesionesTotales.text = totalSessions.toString()
        binding.textMejorando.text = recentImprovement.toString()

        // Actualizar hijo con mejor progreso
        val topChild = progressList.maxByOrNull { getOverallScore(it) }
        topChild?.let {
            binding.textTopChildName.text = it.childName
            binding.textTopChildScore.text = "${getOverallScore(it).toInt()}% progreso"
        }

        // Actualizar recomendaciones
        updateRecommendations(progressList)
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
}