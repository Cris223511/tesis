package com.example.serious_game_usil.presentation.ui.main

import android.app.Application
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.EmotionRetrofitClient
import com.example.serious_game_usil.network.RetrofitClient

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)

        val savedToken = AuthManager.getAccessToken()
        RetrofitClient.setAuthToken(savedToken)
        EmotionRetrofitClient.setAuthToken(savedToken)
    }
}
