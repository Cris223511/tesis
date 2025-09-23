package com.example.serious_game_usil.presentation.ui.administrador.info

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityInfoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class InfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityInfoBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupContactClickListeners()
        updateCopyrightYear()
    }
    
    private fun updateCopyrightYear() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val copyrightText = "© $currentYear Serious Game - Todos los derechos reservados"
        
        // Find the copyright TextView and update its text
        binding.root.findViewById<TextView>(R.id.copyrightText)?.text = copyrightText
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupContactClickListeners() {
        // Email de soporte
        binding.emailSupport.setOnClickListener {
            sendEmail("jhafetkarloz@gmail.com", "Soporte Técnico - Serious Game")
        }
        

        
        binding.emailSupportText.setOnClickListener {
            sendEmail("jhafetkarloz@gmail.com", "Soporte Técnico - Serious Game")
        }
    }
    
    private fun sendEmail(email: String, subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Enviar email con:"))
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró una aplicación de email", Toast.LENGTH_SHORT).show()
        }
    }
}