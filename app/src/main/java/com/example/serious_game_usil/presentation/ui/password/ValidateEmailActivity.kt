package com.example.serious_game_usil.presentation.ui.password

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityValidateEmailBinding
import com.example.serious_game_usil.repository.UserRepository

class ValidateEmailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityValidateEmailBinding
    private lateinit var viewModel: PasswordChangeViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityValidateEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = PasswordChangeViewModelFactory(UserRepository.getInstance(this))
            .create(PasswordChangeViewModel::class.java)
        
        setupUI()
        setupObservers()
    }
    
    private fun setupUI() {
        binding.toolBar.setNavigationOnClickListener { finish() }
        
        binding.continueButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            
            if (email.isEmpty()) {
                binding.emailInputLayout.error = "Ingresa tu correo electrónico"
                return@setOnClickListener
            }
            
            if (!isValidEmail(email)) {
                binding.emailInputLayout.error = "Correo electrónico no válido"
                return@setOnClickListener
            }
            
            binding.emailInputLayout.error = null
            viewModel.validateEmail(email)
        }
    }
    
    private fun setupObservers() {
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.continueButton.isEnabled = !isLoading
            binding.continueButton.text = if (isLoading) "Validando..." else "Continuar"
            binding.emailEditText.isEnabled = !isLoading
        })
        
        viewModel.validationResult.observe(this, Observer { result ->
            result?.let {
                val email = binding.emailEditText.text.toString().trim()
                val intent = Intent(this, PasswordOTPActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
                finish()
            }
        })
        
        viewModel.error.observe(this, Observer { error ->
            error?.let {
                when {
                    it.contains("no existe", ignoreCase = true) -> {
                        binding.emailInputLayout.error = "No existe una cuenta con este correo"
                    }
                    else -> {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}