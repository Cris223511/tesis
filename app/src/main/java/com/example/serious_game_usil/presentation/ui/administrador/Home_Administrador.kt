package com.example.serious_game_usil.presentation.ui.administrador
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.DashboardAdministradorBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.administrador.list.ListUserActivity
import com.example.serious_game_usil.presentation.ui.administrador.roles.ListRoles

import com.example.serious_game_usil.repository.ActivityRepository
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.utils.ActivitiesAdapter
import com.google.android.material.card.MaterialCardView

import com.google.android.material.tabs.TabLayoutMediator
import com.seriousgame.app.navigation.RouteNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: DashboardAdministradorBinding
    private lateinit var activitiesAdapter: ActivitiesAdapter
    private lateinit var viewModel: DashboardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = DashboardAdministradorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Crear ViewModel manualmente
        val factory = DashboardViewModelFactory(
            UserRepository.getInstance(this),
            ActivityRepository.getInstance(this)
        )
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        setupViews()
        observeViewModel()
        updateDateTime()

        binding.logoutButton.setOnClickListener {
            AuthManager.clearSession()
            RouteNavigator.navigateToLogin(this)
            finishAffinity()
        }
    }

    private fun setupViews() {
        setupNavigation()
        // Usar el nombre del usuario autenticado
        binding.userName.text = AuthManager.getNombresApellidos()
    }

    private fun setupNavigation() {
        binding.navChildren.setOnClickListener {
            // Ahora muestra "En desarrollo"
            Toast.makeText(this, "Gestión de niños - En desarrollo", Toast.LENGTH_SHORT).show()
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
                    2 -> Toast.makeText(this, "Mi Perfil - En desarrollo", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        // Re-verificar autenticación cuando la activity vuelve a estar activa
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
        }
    }
}
