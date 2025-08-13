package com.example.serious_game_usil.presentation.ui.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityPasswordOtpBinding
import com.example.serious_game_usil.repository.UserRepository

class PasswordOTPActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPasswordOtpBinding
    private lateinit var viewModel: PasswordChangeViewModel
    
    private var email: String = ""
    private var countDownTimer: CountDownTimer? = null
    private var expirationTimer: CountDownTimer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = PasswordChangeViewModelFactory(UserRepository.getInstance(this))
            .create(PasswordChangeViewModel::class.java)
        
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            finish()
            return
        }
        
        setupUI()
        setupObservers()
        setupOTPTextWatchers()
        
        viewModel.sendPasswordOTP(email)
        startExpirationTimer()
    }
    
    private fun setupUI() {
        binding.toolBar.setNavigationOnClickListener { finish() }
        
        binding.emailText.text = "Código enviado a $email"
        
        binding.verifyButton.setOnClickListener {
            val otpCode = getOTPCode()
            if (otpCode.length == 6) {
                viewModel.verifyPasswordOTP(email, otpCode)
            } else {
                Toast.makeText(this, "Ingresa el código de 6 dígitos", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.resendButton.setOnClickListener {
            expirationTimer?.cancel()
            binding.verifyButton.isEnabled = true
            binding.verifyButton.text = "Verificar Código"
            clearOTPFields()
            viewModel.sendPasswordOTP(email)
        }
    }
    
    private fun setupObservers() {
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.verifyButton.isEnabled = !isLoading && getOTPCode().length == 6
            binding.verifyButton.text = if (isLoading) "Verificando..." else "Verificar Código"
            binding.resendButton.isEnabled = !isLoading
        })
        
        viewModel.otpSent.observe(this, Observer { sent ->
            if (sent) {
                Toast.makeText(this, "Código generado y copiado al portapapeles", Toast.LENGTH_SHORT).show()
                startResendTimer()
                startExpirationTimer()
                viewModel.clearOTPSent()
            }
        })
        
        viewModel.otpCode.observe(this, Observer { otpCode ->
            otpCode?.let { code ->
                // Copiar al portapapeles
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", code)
                clipboard.setPrimaryClip(clip)
                
                // Auto-rellenar los campos
                fillOTPFields(code)
                
                Toast.makeText(this, "Código copiado: $code", Toast.LENGTH_LONG).show()
            }
        })
        
        viewModel.otpVerified.observe(this, Observer { verified ->
            if (verified) {
                val intent = Intent(this, NewPasswordActivity::class.java)
                intent.putExtra("email", email)
                intent.putExtra("otp_code", getOTPCode())
                startActivity(intent)
                finish()
            }
        })
        
        viewModel.error.observe(this, Observer { error ->
            error?.let {
                when {
                    it.contains("inválido", ignoreCase = true) ||
                    it.contains("expirado", ignoreCase = true) -> {
                        clearOTPFields()
                        Toast.makeText(this, "Código incorrecto o expirado", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun setupOTPTextWatchers() {
        val editTexts = listOf(
            binding.otpEditText1, binding.otpEditText2, binding.otpEditText3,
            binding.otpEditText4, binding.otpEditText5, binding.otpEditText6
        )
        
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && index < editTexts.size - 1) {
                        editTexts[index + 1].requestFocus()
                    }
                    
                    val isLoading = viewModel.isLoading.value ?: false
                    binding.verifyButton.isEnabled = getOTPCode().length == 6 && !isLoading
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
            
            editText.setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_DEL && 
                    editText.text?.isEmpty() == true && 
                    index > 0) {
                    editTexts[index - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }
    
    private fun getOTPCode(): String {
        return binding.otpEditText1.text.toString() +
                binding.otpEditText2.text.toString() +
                binding.otpEditText3.text.toString() +
                binding.otpEditText4.text.toString() +
                binding.otpEditText5.text.toString() +
                binding.otpEditText6.text.toString()
    }
    
    private fun clearOTPFields() {
        binding.otpEditText1.text?.clear()
        binding.otpEditText2.text?.clear()
        binding.otpEditText3.text?.clear()
        binding.otpEditText4.text?.clear()
        binding.otpEditText5.text?.clear()
        binding.otpEditText6.text?.clear()
        binding.otpEditText1.requestFocus()
    }
    
    private fun fillOTPFields(code: String) {
        if (code.length == 6) {
            binding.otpEditText1.setText(code[0].toString())
            binding.otpEditText2.setText(code[1].toString())
            binding.otpEditText3.setText(code[2].toString())
            binding.otpEditText4.setText(code[3].toString())
            binding.otpEditText5.setText(code[4].toString())
            binding.otpEditText6.setText(code[5].toString())
            
            // Habilitar botón verificar
            binding.verifyButton.isEnabled = true
        }
    }
    
    private fun startResendTimer() {
        binding.resendButton.isEnabled = false
        
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.resendButton.text = "Reenviar ($seconds s)"
            }
            
            override fun onFinish() {
                binding.resendButton.isEnabled = true
                binding.resendButton.text = "Reenviar código"
            }
        }
        countDownTimer?.start()
    }
    
    private fun startExpirationTimer() {
        binding.expirationTimerText.setTextColor(getColor(R.color.primary))
        
        expirationTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                
                binding.expirationTimerText.text = 
                    String.format("Código válido por: %02d:%02d", minutes, remainingSeconds)
                
                // Cambiar color cuando quedan menos de 10 segundos
                if (seconds <= 10) {
                    binding.expirationTimerText.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
            
            override fun onFinish() {
                binding.expirationTimerText.text = "⏰ Código expirado"
                binding.expirationTimerText.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.verifyButton.isEnabled = false
                binding.verifyButton.text = "Código Expirado"
                clearOTPFields()
                Toast.makeText(this@PasswordOTPActivity, 
                    "El código ha expirado. Solicita uno nuevo.", Toast.LENGTH_LONG).show()
            }
        }
        expirationTimer?.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        expirationTimer?.cancel()
    }
}