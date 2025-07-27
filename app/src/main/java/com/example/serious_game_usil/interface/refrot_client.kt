import com.example.serious_game_usil.BuildConfig
import com.example.serious_game_usil.`interface`.ApiService
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit





object RetrofitClient {
    @Volatile
    private var retrofitWithAuth: Retrofit? = null

    @Volatile
    private var retrofitNoAuth: Retrofit? = null

    @Volatile
    private var authToken: String? = null

    private val tokenLock = Any()

    fun setAuthToken(token: String?) {
        synchronized(tokenLock) {
            authToken = token
            retrofitWithAuth = null
        }
    }

    fun getApiService(): ApiService = getRetrofitWithAuth().create(ApiService::class.java)

    fun getApiServiceNoAuth(): ApiService = getRetrofitNoAuth().create(ApiService::class.java)

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
            clientBuilder.addInterceptor(AuthInterceptor {
                synchronized(tokenLock) { authToken }
            })
        }

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(
                GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    .create()
            ))
            .build()
    }
}

class AuthInterceptor(
    private val tokenProvider: () -> String?
) : okhttp3.Interceptor {

    companion object {
        private val PUBLIC_ENDPOINTS = setOf(
            "/auth/gett",
            "/api/refresh-token",
            "/api/login",
            "/api/otp/validate",
            "/api/otp/resend",
            "/api/login/finish",
            "/api/login/begin",
        )
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (PUBLIC_ENDPOINTS.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        val token = tokenProvider()
        return if (token != null) {
            chain.proceed(
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            )
        } else {
            chain.proceed(request)
        }
    }
}

object TokenManager {
    @Volatile
    private var currentToken: String? = null

    private val tokenLock = Any()

    suspend fun ensureToken(): String? {
        synchronized(tokenLock) {
            currentToken?.let { return it }
        }

        return obtainToken()
    }

    private suspend fun obtainToken(): String? {
        return try {
            val apiService = RetrofitClient.getApiServiceNoAuth()
            val response = apiService.getToken()

            if (response.isSuccessful) {
                response.body()?.token?.let { token ->
                    synchronized(tokenLock) {
                        currentToken = token
                        RetrofitClient.setAuthToken(token)
                    }
                    token
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCurrentToken(): String? = synchronized(tokenLock) { currentToken }

    fun invalidateToken() {
        synchronized(tokenLock) {
            currentToken = null
            RetrofitClient.setAuthToken(null)
        }
    }
}
