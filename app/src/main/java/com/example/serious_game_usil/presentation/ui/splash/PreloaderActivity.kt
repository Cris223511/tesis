package com.example.serious_game_usil.presentation.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.R
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import com.example.serious_game_usil.presentation.ui.onboarding.OnboardingActivity
class PreloaderActivity : AppCompatActivity() {

    private lateinit var logoPreloader: ImageView
    private lateinit var loadingText: TextView
    private lateinit var appName: TextView
    private lateinit var versionText: TextView
    private lateinit var pulseCircle: View

    private val loadingDuration = 2500L
    private val loadingMessages = listOf(
        "Iniciando",
        "Cargando recursos",
        "Preparando interfaz"
    )
    private var messageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preloader)

        // Inicializar vistas
        initViews()

        // Configurar pantalla completa
        setupFullScreen()

        // Configurar version
        setupVersion()

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
        appName = findViewById(R.id.app_name)
        versionText = findViewById(R.id.version_text)
        pulseCircle = findViewById(R.id.pulse_circle)
    }

    private fun setupVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            versionText.text = "Versión $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            versionText.text = "Versión 1.1"
        }
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
        // Fade in con escala del logo
        logoPreloader.alpha = 0f
        logoPreloader.scaleX = 0.8f
        logoPreloader.scaleY = 0.8f

        val logoFadeIn = ObjectAnimator.ofFloat(logoPreloader, "alpha", 0f, 1f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }

        val logoScaleX = ObjectAnimator.ofFloat(logoPreloader, "scaleX", 0.8f, 1f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }

        val logoScaleY = ObjectAnimator.ofFloat(logoPreloader, "scaleY", 0.8f, 1f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Animación de pulso para el círculo
        val pulseScale = ObjectAnimator.ofFloat(pulseCircle, "scaleX", 1f, 1.3f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val pulseScaleY = ObjectAnimator.ofFloat(pulseCircle, "scaleY", 1f, 1.3f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val pulseAlpha = ObjectAnimator.ofFloat(pulseCircle, "alpha", 0.3f, 0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Animación del nombre de la app
        appName.alpha = 0f
        appName.translationY = 20f

        val appNameFade = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 400
        }

        val appNameTranslate = ObjectAnimator.ofFloat(appName, "translationY", 20f, 0f).apply {
            duration = 600
            startDelay = 400
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Crear AnimatorSet para coordinar todas las animaciones
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            logoFadeIn, logoScaleX, logoScaleY,
            pulseScale, pulseScaleY, pulseAlpha,
            appNameFade, appNameTranslate
        )
        animatorSet.start()

        // Animación del texto de carga
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
        startActivity(Intent(this, OnboardingActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}