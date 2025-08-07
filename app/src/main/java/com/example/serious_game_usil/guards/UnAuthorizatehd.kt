package com.example.serious_game_usil.guards

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.serious_game_usil.R
import com.seriousgame.app.navigation.RouteNavigator

class UnauthorizedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unauthorized)

        setupViews()
    }

    private fun setupViews() {
        findViewById<TextView>(R.id.messageText).text =
            "No tienes permisos para acceder a esta sección"

        findViewById<TextView>(R.id.userRolesText).text =
            "Tus roles actuales: ${AuthManager.getUserRoles().joinToString(", ")}"

        findViewById<Button>(R.id.goBackButton).setOnClickListener {
            // Volver a la pantalla principal del usuario
            RouteNavigator.navigateToUserHome(this)
        }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            AuthManager.logout()
            RouteNavigator.navigateToLogin(this)
        }
    }
}