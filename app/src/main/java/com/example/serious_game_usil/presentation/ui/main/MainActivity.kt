package com.example.serious_game_usil.presentation.ui.main

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
    }


    private fun setupUI() {
        // Buscar el TextView sin ViewBinding
        val welcomeText = findViewById<TextView>(R.id.welcome_text)
        welcomeText?.text = "¡Bienvenido a Serious Game!"
    }
}