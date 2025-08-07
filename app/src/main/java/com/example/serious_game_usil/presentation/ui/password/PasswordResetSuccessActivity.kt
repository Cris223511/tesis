package com.example.serious_game_usil.presentation.ui.password

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.databinding.ActivityPasswordResetSuccessBinding
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import com.example.serious_game_usil.presentation.ui.main.MainActivity
import com.example.serious_game_usil.guards.AuthManager

class PasswordResetSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordResetSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        AuthManager.init(this)
        
        setupUI()
        animateCheckmark()
    }

    private fun setupUI() {
        binding.goToLoginButton.setOnClickListener {
            redirectToUserHome()
        }
    }
    
    private fun redirectToUserHome() {
        if (AuthManager.isAuthenticated()) {
            // Usuario sigue autenticado, dirigir al home según su ruta protegida
            val protectedRoute = AuthManager.getProtectedRoute()
            val userRoles = AuthManager.getUserRoles()
            
            val targetActivity = when {
                userRoles.any { it.lowercase() in listOf("admin", "administrador") } -> {
                    MainActivity::class.java // Dashboard de admin
                }
                userRoles.any { it.lowercase() in listOf("padre", "padres", "parent") } -> {
                    MainActivity::class.java // Dashboard de padre
                }
                userRoles.any { it.lowercase() in listOf("hijo", "hijos", "student", "estudiante") } -> {
                    MainActivity::class.java // Dashboard de hijo
                }
                userRoles.any { it.lowercase() in listOf("docente", "teacher", "profesor") } -> {
                    MainActivity::class.java // Dashboard de docente
                }
                userRoles.any { it.lowercase() in listOf("especialista", "specialist") } -> {
                    MainActivity::class.java // Dashboard de especialista
                }
                else -> LoginActivity::class.java
            }
            
            val intent = Intent(this, targetActivity).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (targetActivity == MainActivity::class.java) {
                    putExtra("navigation_route", protectedRoute)
                }
            }
            startActivity(intent)
            finish()
        } else {
            // Usuario no autenticado, dirigir al login
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("password_changed", true)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun animateCheckmark() {
        binding.checkIcon.alpha = 0f
        binding.checkIcon.scaleX = 0.5f
        binding.checkIcon.scaleY = 0.5f

        binding.checkIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .withEndAction {
                // Auto-redirect después de 3 segundos
                binding.checkIcon.postDelayed({
                    redirectToUserHome()
                }, 3000)
            }
            .start()
    }
}