import com.example.serious_game_usil.BuildConfig
import com.example.serious_game_usil.`interface`.ApiService
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {

    private var INSTANCE: ApiService? = null
    private var authToken: String? = null

    fun getApiService(): ApiService {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildApiService().also { INSTANCE = it }
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
        INSTANCE = null
    }

    private fun buildApiService(): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authInterceptor = AuthInterceptor { authToken }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}

class AuthInterceptor(private val tokenProvider: () -> String?) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val token = tokenProvider()

        return if (token != null && !original.url.pathSegments.contains("login") &&
            !original.url.pathSegments.contains("register")) {
            val authorized = original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authorized)
        } else {
            chain.proceed(original)
        }
    }
}