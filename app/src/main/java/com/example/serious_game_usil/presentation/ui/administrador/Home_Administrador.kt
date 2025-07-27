package com.example.serious_game_usil.presentation.ui.administrador
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.DashboardAdministradorBinding
import com.example.serious_game_usil.guards.AuthManager
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
    }

    private fun setupViews() {
        setupNavigation()

        // Usar el nombre del usuario autenticado
        binding.userName.text = AuthManager.getNombresApellidos()
    }



    private fun setupNavigation() {
        binding.navChildren.setOnClickListener {
            updateNavigationSelection(DashboardViewModel.NavigationItem.CHILDREN)
            viewModel.updateNavigationSelection(DashboardViewModel.NavigationItem.CHILDREN)
        }

        binding.navProfile.setOnClickListener {
            updateNavigationSelection(DashboardViewModel.NavigationItem.PROFILE)
            viewModel.updateNavigationSelection(DashboardViewModel.NavigationItem.PROFILE)
        }

        binding.navInfo.setOnClickListener {
            updateNavigationSelection(DashboardViewModel.NavigationItem.INFO)
            viewModel.updateNavigationSelection(DashboardViewModel.NavigationItem.INFO)
        }

        // Perfil seleccionado por defecto
        updateNavigationSelection(DashboardViewModel.NavigationItem.PROFILE)
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

    private fun updateNavigationSelection(selectedItem: DashboardViewModel.NavigationItem) {
        // Resetear todos
        listOf(binding.navChildren, binding.navProfile, binding.navInfo).forEach { navItem ->
            navItem.background = null
            updateNavItemStyle(navItem, false)
        }

        // Aplicar estilo seleccionado
        when (selectedItem) {
            DashboardViewModel.NavigationItem.CHILDREN -> {
                binding.navChildren.background = ContextCompat.getDrawable(this, R.drawable.nav_selected_bg)
                updateNavItemStyle(binding.navChildren, true)
            }
            DashboardViewModel.NavigationItem.PROFILE -> {
                binding.navProfile.background = ContextCompat.getDrawable(this, R.drawable.nav_selected_bg)
                updateNavItemStyle(binding.navProfile, true)
            }
            DashboardViewModel.NavigationItem.INFO -> {
                binding.navInfo.background = ContextCompat.getDrawable(this, R.drawable.nav_selected_bg)
                updateNavItemStyle(binding.navInfo, true)
            }
        }
    }

    private fun updateNavItemStyle(navItem: LinearLayout, isSelected: Boolean) {
        val cardView = navItem.getChildAt(0) as MaterialCardView
        val textView = navItem.getChildAt(1) as TextView
        val imageView = cardView.getChildAt(0) as ImageView

        if (isSelected) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            imageView.imageTintList = ContextCompat.getColorStateList(this, R.color.on_primary)
            textView.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            textView.setTypeface(textView.typeface, android.graphics.Typeface.BOLD)
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.nav_icon_bg_inactive))

            // Aplicar tint según el elemento
            when (navItem.id) {
                R.id.navChildren, R.id.navProfile -> {
                    imageView.imageTintList = ContextCompat.getColorStateList(this, R.color.primary)
                }
                R.id.navInfo -> {
                    imageView.imageTintList = ContextCompat.getColorStateList(this, R.color.info_color)
                }
            }

            textView.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            textView.setTypeface(textView.typeface, android.graphics.Typeface.NORMAL)
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
