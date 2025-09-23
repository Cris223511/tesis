package com.seriousgame.app.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.serious_game_usil.databinding.DashboardAdministradorBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.guards.UnauthorizedActivity
import com.example.serious_game_usil.presentation.ui.administrador.DashboardActivity
import com.example.serious_game_usil.presentation.ui.administrador.list.ListUserActivity
import com.example.serious_game_usil.presentation.ui.administrador.roles.ListRoles
import com.example.serious_game_usil.presentation.ui.padres.PadresDashboardActivity
import com.example.serious_game_usil.presentation.ui.login.LoginActivity

object RouteNavigator {

    fun navigateToProtectedRoute(context: Context, route: String) {
        if (!AuthManager.isAuthenticated()) {
            navigateToLogin(context)
            return
        }

        if (!AuthManager.canAccessRoute(route)) {
            navigateToUnauthorized(context)
            return
        }

        val intent = when (route) {
            "/admin", "/admin/dashboard" -> {
                Intent(context, DashboardActivity::class.java)
            }
            "/admin/list-user" -> {
                Intent(context, ListUserActivity::class.java)
            }
            "/admin/list-roles" -> {
                Intent(context, ListRoles::class.java)
            }
            "/emotion-analysis" -> {
                Intent(context, com.example.serious_game_usil.presentation.ui.emotion.EmotionAnalysisActivity::class.java)
            }
            "/terapeuta", "/terapeuta/dashboard", "/therapist", "/therapist/dashboard" -> {
                Intent(context, PadresDashboardActivity::class.java)
            }
            else -> {
                Intent(context, DashboardActivity::class.java)
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)

        // Finalizar la activity si el context es una Activity
        (context as? Activity)?.finish()
    }

    fun navigateToUserHome(context: Context) {
        val protectedRoute = AuthManager.getProtectedRoute()
        navigateToProtectedRoute(context, protectedRoute)
    }

    fun navigateToLogin(context: Context) {
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)

        // Finalizar la activity si el context es una Activity
        (context as? Activity)?.finish()
    }

    fun navigateToUnauthorized(context: Context) {
        val intent = Intent(context, UnauthorizedActivity::class.java)
        context.startActivity(intent)
    }

    fun checkAuthAndNavigate(activity: Activity, requiredRole: String? = null): Boolean {
        if (!AuthManager.isAuthenticated()) {
            navigateToLogin(activity)
            return false
        }

        requiredRole?.let {
            if (!AuthManager.hasRole(it)) {
                navigateToUnauthorized(activity)
                return false
            }
        }

        return true
    }

    fun navigateToEmotionAnalysis(context: Context) {
        navigateToProtectedRoute(context, "/emotion-analysis")
    }
}