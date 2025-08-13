package com.example.serious_game_usil.presentation.ui.administrador
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.DashboardAdministradorBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.administrador.list.ListUserActivity
import com.example.serious_game_usil.presentation.ui.administrador.roles.ListRoles
import com.example.serious_game_usil.presentation.ui.padres.PadresDashboardActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressViewModel
import com.example.serious_game_usil.presentation.ui.progress.ThreeMonthComparisonFragment
import com.example.serious_game_usil.presentation.ui.progress.ProgressDetailActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressAlertManager

import com.example.serious_game_usil.repository.ActivityRepository
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.utils.ActivitiesAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView

import com.google.android.material.tabs.TabLayoutMediator
import com.bumptech.glide.Glide
import com.example.serious_game_usil.utils.ImageUtils
import com.seriousgame.app.navigation.RouteNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: DashboardAdministradorBinding
    private lateinit var activitiesAdapter: ActivitiesAdapter
    private lateinit var viewModel: DashboardViewModel
    private lateinit var progressViewModel: ProgressViewModel
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = DashboardAdministradorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar Navigation Drawer
        setupNavigationDrawer()

        // Crear ViewModel manualmente
        val factory = DashboardViewModelFactory(
            UserRepository.getInstance(this),
            ActivityRepository.getInstance(this)
        )
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
        progressViewModel = ViewModelProvider(this)[ProgressViewModel::class.java]

        setupViews()
        observeViewModel()
        setupProgressObservers()
        updateDateTime()
        
        // Cargar progreso de todos los niños
        progressViewModel.loadAllChildrenProgress()

        binding.logoutButton.setOnClickListener {
            AuthManager.clearSession()
            RouteNavigator.navigateToLogin(this)
            finishAffinity()
        }
        
        // Click en la card de comparación para ver detalles
        binding.comparisonCard.setOnClickListener {
            showDetailedProgressView()
        }
    }

    private fun setupViews() {
        setupNavigation()
        // Usar el nombre del usuario autenticado
        binding.userName.text = AuthManager.getNombresApellidos()
        
        // Cargar foto del usuario si está disponible
        val userPhoto = AuthManager.getFoto()
        android.util.Log.d("DashboardActivity", "UserPhoto from AuthManager: '$userPhoto'")
        ImageUtils.loadUserPhoto(this, userPhoto, binding.userAvatar)
    }

    private fun setupNavigation() {
        binding.navChildren.setOnClickListener {
            // Navegar a vista de progreso terapéutico
            showTherapyProgressOptions()
        }

        binding.navProfile.setOnClickListener {
            // Acción directa: mostrar menú de opciones de perfil
            showProfileOptions()
        }

        binding.navInfo.setOnClickListener {
            // Acción directa: mostrar información/estadísticas
            showInfoOptions()
        }
    }

    private fun showProfileOptions() {
        val options = arrayOf("Ver Roles", "Gestionar Usuarios", "Mi Perfil", "Configuración")

        AlertDialog.Builder(this)
            .setTitle("Opciones del administrador")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, ListRoles::class.java)
                        startActivity(intent)
                    }
                    1 -> {
                        // Gestionar Usuarios - ir a ListUserActivity
                        val intent = Intent(this, ListUserActivity::class.java)
                        startActivity(intent)
                    }
                    2 -> {
                        // Navegar a Mi Perfil (sin USER_ID para cargar perfil actual)
                        val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.profile.ProfileActivity::class.java)
                        startActivity(intent)
                    }
                    3 -> Toast.makeText(this, "Configuración - En desarrollo", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showInfoOptions() {
        val options = arrayOf("Estadísticas del Sistema", "Logs de Actividad", "Acerca de")

        AlertDialog.Builder(this)
            .setTitle("Información")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Estadísticas - En desarrollo", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Logs - En desarrollo", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Versión 1.0.0", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun showTherapyProgressOptions() {
        val options = arrayOf("Ver progreso de todos los niños", "Registrar nueva sesión", "Estadísticas detalladas")

        AlertDialog.Builder(this)
            .setTitle("Progreso Terapéutico")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showDetailedProgressView()
                    }
                    1 -> {
                        Toast.makeText(this, "Registro de sesiones - En desarrollo", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        Toast.makeText(this, "Estadísticas detalladas - En desarrollo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
    
    private fun showDetailedProgressView() {
        // Navegar a la Activity de progreso detallado
        val intent = Intent(this, ProgressDetailActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupProgressObservers() {
        progressViewModel.allChildrenProgress.observe(this) { progressList ->
            progressList?.let {
                updateDashboardWithProgressData(it)
            }
        }
        
        progressViewModel.error.observe(this) { error ->
            error?.let {
                // Mostrar datos en 0 si hay error
                updateDashboardWithEmptyData()
            }
        }
    }
    
    private fun updateDashboardWithProgressData(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        if (progressList.isEmpty()) {
            updateDashboardWithEmptyData()
            return
        }
        
        // Calcular estadísticas generales
        val totalSessions = progressList.sumOf { it.summary.totalSessions3M }
        val avgProgress = progressList.map { it.currentMonth?.let { metrics -> 
            (metrics.avgSocialInteraction + metrics.avgCommunication + metrics.avgSensoryProcessing + 
             metrics.avgAttentionFocus + metrics.avgEmotionalRegulation) / 5.0
        } ?: 0.0 }.average()
        
        // Actualizar las cards de estadísticas con datos reales
        updateStatsCards(totalSessions, avgProgress.toInt())
        
        // Actualizar la sección de comparación con datos reales
        updateComparisonSection(progressList)
        
        // Verificar y mostrar alertas de progreso
        ProgressAlertManager.checkAndShowAlerts(this, progressList)
    }
    
    private fun updateDashboardWithEmptyData() {
        // Mostrar 0s cuando no hay datos
        updateStatsCards(0, 0)
        updateComparisonSectionEmpty()
    }
    
    private fun updateStatsCards(totalSessions: Int, avgProgress: Int) {
        // Convertir total de sesiones a formato hora:minuto
        val hours = totalSessions / 4 // Asumiendo sesiones de 15 min promedio
        val minutes = (totalSessions % 4) * 15
        
        // Actualizar primera card (tiempo de estímulo)
        val stimulusCard = binding.statsContainer.getChildAt(0) as MaterialCardView
        val stimulusContainer = stimulusCard.getChildAt(0) as LinearLayout
        val stimulusTimeContainer = stimulusContainer.getChildAt(1) as LinearLayout
        
        (stimulusTimeContainer.getChildAt(0) as TextView).text = hours.toString().padStart(2, '0')
        (stimulusTimeContainer.getChildAt(2) as TextView).text = minutes.toString().padStart(2, '0')
        
        // Actualizar segunda card con progreso pendiente
        val pendingProgress = 100 - avgProgress
        val pendingHours = pendingProgress / 20 // Escalar para mostrar como horas
        val pendingMinutes = (pendingProgress % 20) * 3
        
        val pendingCard = binding.statsContainer.getChildAt(1) as MaterialCardView
        val pendingContainer = pendingCard.getChildAt(0) as LinearLayout
        val pendingTimeContainer = pendingContainer.getChildAt(1) as LinearLayout
        
        (pendingTimeContainer.getChildAt(0) as TextView).text = pendingHours.toString().padStart(2, '0')
        (pendingTimeContainer.getChildAt(2) as TextView).text = pendingMinutes.toString().padStart(2, '0')
    }
    
    private fun updateComparisonSection(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        // Actualizar títulos con datos reales
        binding.lastMonthsTitle.text = "Progreso de ${progressList.size} niños"
        binding.lastMonthsSubtitle.text = "Comparativa terapéutica de los últimos 3 meses"
        
        if (progressList.isNotEmpty()) {
            updateProgressBars(progressList)
        }
    }
    
    private fun updateProgressBars(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        // Calcular estadísticas agregadas por mes
        val ninosData = calculateMonthStats(progressList, "Niños")
        val ninasData = calculateMonthStats(progressList, "Niñas")
        
        // Actualizar primera barra (Agosto - Niños)
        updateProgressBar(
            binding.comparisonCard,
            0, // Primera sección
            "Ago",
            "Niños", 
            ninosData.score,
            "${ninosData.count} niños con progreso"
        )
        
        // Actualizar segunda barra (Julio - Niñas) 
        updateProgressBar(
            binding.comparisonCard,
            1, // Segunda sección
            "Jul", 
            "Niñas",
            ninasData.score,
            "${ninasData.count} niñas con progreso"
        )
    }
    
    private fun calculateMonthStats(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>, category: String): ProgressStats {
        if (progressList.isEmpty()) return ProgressStats(0, 0)
        
        // Filtrar por categoría basado en algún criterio (por ejemplo, género del nombre o ID par/impar)
        val filtered = if (category == "Niños") {
            progressList.filterIndexed { index, _ -> index % 2 == 0 }
        } else {
            progressList.filterIndexed { index, _ -> index % 2 == 1 }
        }
        
        if (filtered.isEmpty()) return ProgressStats(0, 0)
        
        // Calcular promedio de progreso
        val avgProgress = filtered.map { comparison ->
            comparison.currentMonth?.let { metrics ->
                (metrics.avgSocialInteraction + metrics.avgCommunication + 
                 metrics.avgSensoryProcessing + metrics.avgAttentionFocus + 
                 metrics.avgEmotionalRegulation) / 5.0
            } ?: 0.0
        }.average()
        
        return ProgressStats(avgProgress.toInt(), filtered.size)
    }
    
    private fun updateProgressBar(cardView: com.google.android.material.card.MaterialCardView, sectionIndex: Int, month: String, category: String, score: Int, description: String) {
        try {
            val cardLayout = cardView.getChildAt(0) as LinearLayout
            val section = cardLayout.getChildAt(if (sectionIndex == 0) 0 else 2) as LinearLayout // Skip separador
            
            // Actualizar mes
            val monthText = section.getChildAt(0) as TextView
            monthText.text = month
            
            // Actualizar datos de la barra
            val dataLayout = section.getChildAt(1) as LinearLayout
            val barLayout = dataLayout.getChildAt(0) as LinearLayout
            
            // Actualizar categoría
            val categoryText = barLayout.getChildAt(0) as TextView
            categoryText.text = category
            
            // Actualizar barra de progreso
            val progressContainer = barLayout.getChildAt(1) as FrameLayout
            val progressBar = progressContainer.getChildAt(0) as View
            
            // Calcular ancho de la barra (score de 0-100 convertido a porcentaje del ancho)
            val params = progressBar.layoutParams as FrameLayout.LayoutParams
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.rightMargin = ((100 - score) * progressContainer.width / 100).coerceAtLeast(0)
            progressBar.layoutParams = params
            
            // Actualizar valor numérico
            val scoreText = barLayout.getChildAt(2) as TextView
            scoreText.text = score.toString()
            
            // Actualizar descripción
            val descriptionText = dataLayout.getChildAt(1) as TextView
            descriptionText.text = description
            
        } catch (e: Exception) {
            // Si hay error en la actualización dinámica, mantener valores estáticos
            android.util.Log.e("DashboardActivity", "Error updating progress bar: ${e.message}")
        }
    }
    
    private fun updateComparisonSectionEmpty() {
        binding.lastMonthsTitle.text = "Los últimos 3 meses"
        binding.lastMonthsSubtitle.text = "No hay datos de progreso registrados aún"
        
        // Actualizar con valores en 0
        updateProgressBar(binding.comparisonCard, 0, "Ago", "Niños", 0, "0 / 0 niños revisados")
        updateProgressBar(binding.comparisonCard, 1, "Jul", "Niñas", 0, "0 / 0 niñas revisadas")
    }

    private fun observeViewModel() {
        viewModel.user.observe(this) { user ->
            user?.let {
                // Usar el nombre de AuthManager
                binding.userName.text = AuthManager.getNombresApellidos()
                // Sin Glide, solo usar el placeholder
                binding.userAvatar.setImageResource(R.drawable.ic_person)
            }
        }

        viewModel.activities.observe(this) { activities ->
            activities?.let {
                activitiesAdapter = ActivitiesAdapter(it) { activity ->
                    viewModel.onActivitySelected(activity)
                }
            }
        }

        viewModel.stimulusTime.observe(this) { (hours, minutes) ->
            // Acceder directamente a los elementos por posición
            val stimulusCard = binding.statsContainer.getChildAt(0) as MaterialCardView
            val container = stimulusCard.getChildAt(0) as LinearLayout
            val timeContainer = container.getChildAt(1) as LinearLayout

            // Primer TextView = horas
            (timeContainer.getChildAt(0) as TextView).text = hours.toString().padStart(2, '0')
            // Tercer TextView = minutos (el segundo es el separador)
            (timeContainer.getChildAt(2) as TextView).text = minutes.toString().padStart(2, '0')
        }

        viewModel.pendingTime.observe(this) { (hours, minutes) ->
            // Segunda tarjeta
            val pendingCard = binding.statsContainer.getChildAt(1) as MaterialCardView
            val container = pendingCard.getChildAt(0) as LinearLayout
            val timeContainer = container.getChildAt(1) as LinearLayout

            (timeContainer.getChildAt(0) as TextView).text = hours.toString().padStart(2, '0')
            (timeContainer.getChildAt(2) as TextView).text = minutes.toString().padStart(2, '0')
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun updateDateTime() {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("dd 'de' MMMM 'del' yyyy, 'a las' hh:mm a", Locale("es", "ES"))

        // Acceder al segundo TextView del todayCard
        val todayCardContent = binding.todayCard.getChildAt(0) as LinearLayout
        val dateTextView = todayCardContent.getChildAt(1) as TextView
        dateTextView.text = dateFormat.format(currentDate)
    }

    private fun setupNavigationDrawer() {
        // Configurar el Navigation Drawer
        binding.navView.setNavigationItemSelectedListener(this)
        
        // Cargar foto del usuario en el header del drawer
        val headerView = binding.navView.getHeaderView(0)
        val drawerUserImage = headerView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imageView)
        
        val userPhoto = AuthManager.getFoto()
        android.util.Log.d("DashboardActivity", "UserPhoto for drawer: '$userPhoto'")
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
            R.id.nav_admin_dashboard -> {
                // Ya estamos en el dashboard admin
            }
            R.id.nav_padres_dashboard -> {
                startActivity(Intent(this, PadresDashboardActivity::class.java))
            }
            R.id.nav_children_list -> {
                startActivity(Intent(this, ProgressDetailActivity::class.java))
            }
            R.id.nav_users -> {
                startActivity(Intent(this, ListUserActivity::class.java))
            }
            R.id.nav_roles -> {
                startActivity(Intent(this, ListRoles::class.java))
            }
            R.id.nav_reports -> {
                Toast.makeText(this, "Reportes - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_settings -> {
                Toast.makeText(this, "Configuración - Próximamente", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
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

    override fun onResume() {
        super.onResume()
        // Re-verificar autenticación cuando la activity vuelve a estar activa
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
        }
    }
}

// Data class para estadísticas de progreso
data class ProgressStats(
    val score: Int,    // 0-100
    val count: Int     // cantidad de niños/niñas
)
