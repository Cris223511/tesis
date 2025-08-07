package com.example.serious_game_usil.presentation.ui.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.R
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
class PreloaderActivity : AppCompatActivity() {

    private lateinit var logoPreloader: ImageView
    private lateinit var loadingText: TextView

    private val loadingDuration = 3000L
    private val loadingMessages = listOf(
        "CARGANDO",
        "PREPARANDO",
        "INICIANDO"
    )
    private var messageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preloader)

        // Inicializar vistas
        initViews()

        // Configurar pantalla completa
        setupFullScreen()

        // Iniciar animaciones
        startAnimations()

        // Navegar después de cargar
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToLogin()
        }, loadingDuration)
    }

    private fun initViews() {
        logoPreloader = findViewById(R.id.logo_preloader)
        loadingText = findViewById(R.id.loading_text)
    }

    private fun setupFullScreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun startAnimations() {
        // Animación del logo
        val logoRotation = ObjectAnimator.ofFloat(logoPreloader, "rotation", 0f, 360f).apply {
            duration = 20000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        // Iniciar animaciones
        logoRotation.start()

        // Animación del texto
        animateLoadingText()
    }

    private fun animateLoadingText() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                // Fade out
                loadingText.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        // Cambiar texto
                        loadingText.text = loadingMessages[messageIndex]
                        messageIndex = (messageIndex + 1) % loadingMessages.size

                        // Fade in
                        loadingText.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()

                handler.postDelayed(this, 1200)
            }
        }

        handler.postDelayed(runnable, 1200)
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}