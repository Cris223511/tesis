package com.example.serious_game_usil.presentation.ui.recuperation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.databinding.ActivityPasswordResetSuccessBinding
import com.example.serious_game_usil.presentation.ui.login.LoginActivity

class PasswordResetSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordResetSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        animateCheckmark()
    }

    private fun setupUI() {
        binding.goToLoginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            .start()
    }
}