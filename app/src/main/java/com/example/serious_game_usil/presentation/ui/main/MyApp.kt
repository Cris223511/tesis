package com.example.serious_game_usil.presentation.ui.main

import android.app.Application
import com.example.serious_game_usil.guards.AuthManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
    }
}