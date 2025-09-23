package com.example.serious_game_usil.presentation.ui.terapeuta

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.DashboardTerapeutaBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.patients.PatientsListActivity
import com.example.serious_game_usil.presentation.ui.progress.ProgressDetailActivity
import com.example.serious_game_usil.utils.ImageUtils
import com.google.android.material.navigation.NavigationView
import com.seriousgame.app.navigation.RouteNavigator

class TerapeutaDrawerActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

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
        setupNavigationDrawer()
        setupQuickActions()
    }

    private fun setupViews() {
        // Usar el nombre del usuario autenticado
        binding.userName.text = AuthManager.getNombresApellidos()

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

    private fun setupNavigationDrawer() {
        // Configurar el Navigation Drawer
        binding.navView.setNavigationItemSelectedListener(this)

        // Cargar foto del usuario en el header del drawer
        val headerView = binding.navView.getHeaderView(0)
        val drawerUserImage = headerView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imageView)

        val userPhoto = AuthManager.getFoto()
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

    private fun setupQuickActions() {
        // Botón de pacientes
        binding.navPatients.setOnClickListener {
            val intent = Intent(this, PatientsListActivity::class.java)
            startActivity(intent)
        }

        // Botón de progreso
        binding.navProgress.setOnClickListener {
            val intent = Intent(this, ProgressDetailActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_terapeuta_dashboard -> {
                // Ya estamos en el dashboard terapeuta
            }
            R.id.nav_patients_list -> {
                startActivity(Intent(this, PatientsListActivity::class.java))
            }
            R.id.nav_patient_progress -> {
                startActivity(Intent(this, ProgressDetailActivity::class.java))
            }
            R.id.nav_emotion_analysis -> {
                startActivity(Intent(this, com.example.serious_game_usil.presentation.ui.emotion.EmotionAnalysisActivity::class.java))
            }
            R.id.nav_register_session -> {
                Toast.makeText(this, "Registrar Sesión - En desarrollo", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_session_history -> {
                Toast.makeText(this, "Historial de Sesiones - En desarrollo", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_therapist_profile -> {
                val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.profile.ProfileActivity::class.java)
                startActivity(intent)
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