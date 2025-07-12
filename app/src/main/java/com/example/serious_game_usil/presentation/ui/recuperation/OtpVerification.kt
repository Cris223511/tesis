package com.example.serious_game_usil.presentation.ui.recuperation

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.databinding.ActivityOtpVerificationBinding

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var otpFields: List<EditText>
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userEmail = intent.getStringExtra("email") ?: ""
        setupUI()
        setupOtpFields()
    }

    private fun setupUI() {
        binding.emailText.text = userEmail

        binding.verifyButton.setOnClickListener {
            verifyOtp()
        }

        binding.resendText.setOnClickListener {
            resendCode()
        }
    }

    private fun setupOtpFields() {
        otpFields = listOf(
            binding.otp1,
            binding.otp2,
            binding.otp3,
            binding.otp4,
            binding.otp5,
            binding.otp6
        )

        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.size - 1) {
                        otpFields[index + 1].requestFocus()
                    }
                }
            })

            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        otpFields[index - 1].requestFocus()
                        otpFields[index - 1].setText("")
                    }
                }
                false
            }
        }
    }

    private fun getOtpCode(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }

    private fun verifyOtp() {
        val otp = getOtpCode()

        when {
            otp.length < 6 -> {
                Toast.makeText(this, "Ingresa el código completo", Toast.LENGTH_SHORT).show()
            }
            otp != "123456" -> { // Código de prueba
                Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
                clearOtpFields()
            }
            else -> {
                binding.verifyButton.isEnabled = false
                binding.verifyButton.text = "Verificando..."

                binding.root.postDelayed({
                    startActivity(Intent(this, ResetPasswordActivity::class.java))
                    finish()
                }, 1500)
            }
        }
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.setText("") }
        otpFields[0].requestFocus()
    }

    private fun resendCode() {
        binding.resendText.isEnabled = false
        Toast.makeText(this, "Código reenviado a $userEmail", Toast.LENGTH_LONG).show()

        binding.root.postDelayed({
            binding.resendText.isEnabled = true
        }, 30000) // 30 segundos de espera
    }
}