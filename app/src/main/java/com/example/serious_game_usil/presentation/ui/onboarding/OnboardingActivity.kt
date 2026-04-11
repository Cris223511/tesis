package com.example.serious_game_usil.presentation.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.serious_game_usil.R
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var skipButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var indicator1: View
    private lateinit var indicator2: View
    private lateinit var indicator3: View
    private lateinit var indicator4: View
    private lateinit var indicator5: View

    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0
    private val totalPages = 5
    private var isPaused = false
    private var lastInteractionTime = 0L
    private val pauseDuration = 5000L // Pausa de 5 segundos después de interacción

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && System.currentTimeMillis() - lastInteractionTime > pauseDuration) {
                if (currentPage == totalPages - 1) {
                    currentPage = 0
                } else {
                    currentPage++
                }
                viewPager.setCurrentItem(currentPage, true)
            }
            handler.postDelayed(this, 3000)
        }
    }

    companion object {
        private const val PREFS_NAME = "OnboardingPrefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldSkipOnboarding()) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_onboarding)
        initViews()
        setupViewPager()
        setupButtons()
    }

    private fun shouldSkipOnboarding(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        skipButton = findViewById(R.id.skipButton)
        continueButton = findViewById(R.id.continueButton)
        indicator1 = findViewById(R.id.indicator1)
        indicator2 = findViewById(R.id.indicator2)
        indicator3 = findViewById(R.id.indicator3)
        indicator4 = findViewById(R.id.indicator4)
        indicator5 = findViewById(R.id.indicator5)
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter()
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicators(position)
                updateContinueButton(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        // Usuario está arrastrando, pausar auto-scroll
                        isPaused = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        // Reanudar auto-scroll después del retraso
                        isPaused = false
                    }
                }
            }
        })

        handler.postDelayed(autoScrollRunnable, 3000)
    }

    private fun updateIndicators(position: Int) {
        indicator1.setBackgroundResource(if (position == 0) R.drawable.indicator_active else R.drawable.indicator_inactive)
        indicator2.setBackgroundResource(if (position == 1) R.drawable.indicator_active else R.drawable.indicator_inactive)
        indicator3.setBackgroundResource(if (position == 2) R.drawable.indicator_active else R.drawable.indicator_inactive)
        indicator4.setBackgroundResource(if (position == 3) R.drawable.indicator_active else R.drawable.indicator_inactive)
        indicator5.setBackgroundResource(if (position == 4) R.drawable.indicator_active else R.drawable.indicator_inactive)
    }

    private fun updateContinueButton(position: Int) {
        continueButton.text = if (position == totalPages - 1) "Comenzar" else "Continuar"
    }

    private fun setupButtons() {
        skipButton.setOnClickListener {
            lastInteractionTime = System.currentTimeMillis()
            navigateToLogin()
        }

        continueButton.setOnClickListener {
            lastInteractionTime = System.currentTimeMillis()
            if (viewPager.currentItem == totalPages - 1) {
                saveOnboardingCompleted()
                navigateToLogin()
            } else {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }
    }

    private fun saveOnboardingCompleted() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoScrollRunnable)
    }

    inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingViewHolder>() {

        private val pages = listOf(
            OnboardingPage(
                R.drawable.onboarding_welcome,
                "Transformando la Terapia Infantil",
                "Tecnología innovadora que convierte las sesiones terapéuticas en experiencias interactivas y divertidas para los niños"
            ),
            OnboardingPage(
                R.drawable.onboarding_therapy,
                "Inteligencia Artificial Avanzada",
                "Análisis facial en tiempo real para detectar emociones y adaptar las actividades terapéuticas de forma personalizada"
            ),
            OnboardingPage(
                R.drawable.onboarding_features,
                "Juegos Terapéuticos Interactivos",
                "Minijuegos diseñados por especialistas: rompecabezas emocionales, laberintos de atención, memoria visual y actividades de coordinación motriz adaptadas a cada edad"
            ),
            OnboardingPage(
                R.drawable.onboarding_security,
                "Máxima Seguridad de Datos",
                "Sin almacenamiento en la nube. Datos encriptados. Sin publicidad. Control de Acceso. Cumple estándares internacionales para protección infantil"
            ),
            OnboardingPage(
                R.drawable.onboarding_progress,
                "Resultados Medibles",
                "Dashboards intuitivos con métricas precisas del progreso emocional y conductual de cada paciente"
            )
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.onboarding_page_layout, parent, false)
            return OnboardingViewHolder(view)
        }

        override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount() = pages.size
    }

    inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.onboardingImage)
        private val title: TextView = itemView.findViewById(R.id.onboardingTitle)
        private val description: TextView = itemView.findViewById(R.id.onboardingDescription)

        fun bind(page: OnboardingPage) {
            image.setImageResource(page.imageRes)
            title.text = page.title
            description.text = page.description
        }
    }

    data class OnboardingPage(
        val imageRes: Int,
        val title: String,
        val description: String
    )
}