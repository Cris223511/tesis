package com.example.serious_game_usil.`interface`

import android.util.Log
import com.example.serious_game_usil.BuildConfig
import com.example.serious_game_usil.network.RetrofitClient
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object EmotionRetrofitClient {
    @Volatile
    private var retrofitWithAuth: Retrofit? = null

    @Volatile
    private var retrofitNoAuth: Retrofit? = null

    @Volatile
    private var authToken: String? = null

    private val tokenLock = Any()

    private const val EMOTION_BASE_URL_DEBUG = "https://10.0.2.2:5001/"
    private const val EMOTION_BASE_URL_RELEASE = "https://tesis-ml-clean-latest.onrender.com/"

    fun setAuthToken(token: String?) {
        synchronized(tokenLock) {
            authToken = token
            retrofitWithAuth = null
        }
    }

    fun getEmotionApiService(): EmotionApiService = getRetrofitWithAuth().create(EmotionApiService::class.java)

    fun getEmotionApiServiceNoAuth(): EmotionApiService = getRetrofitNoAuth().create(EmotionApiService::class.java)

    private fun getRetrofitWithAuth(): Retrofit {
        return retrofitWithAuth ?: synchronized(this) {
            retrofitWithAuth ?: buildRetrofit(true).also { retrofitWithAuth = it }
        }
    }

    private fun getRetrofitNoAuth(): Retrofit {
        return retrofitNoAuth ?: synchronized(this) {
            retrofitNoAuth ?: buildRetrofit(false).also { retrofitNoAuth = it }
        }
    }

    private fun buildRetrofit(includeAuth: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (includeAuth) {
            clientBuilder.addInterceptor(EmotionAuthInterceptor {
                synchronized(tokenLock) { authToken }
            })
        }

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        val baseUrl = if (BuildConfig.DEBUG) EMOTION_BASE_URL_DEBUG else EMOTION_BASE_URL_RELEASE

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(
                GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .create()
            ))
            .build()
    }

    // Sincronizar el token con el AuthManager
    fun syncWithAuthManager(token: String?) {
        setAuthToken(token)
    }
}

class EmotionAuthInterceptor(
    private val tokenProvider: () -> String?
) : okhttp3.Interceptor {

    companion object {
        private val PUBLIC_ENDPOINTS = setOf(
            "/api/v1/health"
        )
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val path = request.url.encodedPath

        Log.d("EmotionAuthInterceptor", "Interceptando request a: $path")

        if (PUBLIC_ENDPOINTS.any { path.contains(it) }) {
            Log.d("EmotionAuthInterceptor", "Endpoint público, sin auth")
            return chain.proceed(request)
        }

        val token = tokenProvider()

        if (token != null) {
            Log.d("EmotionAuthInterceptor", "Token disponible para servicio de emociones")

            val newRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()

            return chain.proceed(newRequest)
        } else {
            Log.d("EmotionAuthInterceptor", "No hay token disponible")
            return chain.proceed(request)
        }
    }
}

// Manager para sincronizar tokens entre servicios
object EmotionTokenManager {
    fun syncTokens() {
        // Obtener token del AuthManager del servicio de usuarios
        val currentToken = com.example.serious_game_usil.guards.AuthManager.getAccessToken()

        // Sincronizar con ambos clientes Retrofit
        RetrofitClient.setAuthToken(currentToken)
        EmotionRetrofitClient.setAuthToken(currentToken)
    }

    fun clearTokens() {
        RetrofitClient.setAuthToken(null)
        EmotionRetrofitClient.setAuthToken(null)
    }
}