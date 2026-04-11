package com.example.serious_game_usil.security

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object NetworkSecurity {

    const val CONNECT_TIMEOUT = 15L
    const val READ_TIMEOUT = 20L
    const val WRITE_TIMEOUT = 20L

    private val connectionPool = ConnectionPool(
        maxIdleConnections = 5,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    )

    fun createSecureOkHttpClient(context: Context, isDebug: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(true)

        if (isDebug) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader("Authorization")
                redactHeader("Cookie")
            }
            builder.addInterceptor(loggingInterceptor)
        }

        if (!isDebug) {
            val certificatePinner = CertificatePinner.Builder()
                .build()
            builder.certificatePinner(certificatePinner)
        }

        builder.addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-App-Version", getAppVersion())
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }

        return builder.build()
    }

    private fun getAppVersion(): String {
        return "1.1"
    }

    fun createSSLContext(): SSLContext {
        return SSLContext.getInstance("TLSv1.3").apply {
            init(null, null, null)
        }
    }
}