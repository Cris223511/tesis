package com.example.serious_game_usil.performance

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class NetworkOptimizer {
    private val requestQueue = Channel<NetworkRequest>(Channel.UNLIMITED)
    private val activeRequests = AtomicInteger(0)
    private val maxConcurrentRequests = 3
    private val requestCache = ConcurrentHashMap<String, CachedResponse>()
    private val cacheTimeout = 60000L

    init {
        GlobalScope.launch(Dispatchers.IO) {
            processRequestQueue()
        }
    }

    data class NetworkRequest(
        val url: String,
        val completion: CompletableDeferred<String>
    )

    data class CachedResponse(
        val data: String,
        val timestamp: Long
    )

    suspend fun <T> executeWithRetry(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> Response<T>
    ): Response<T>? {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                val response = block()
                if (response.isSuccessful) {
                    return response
                }
            } catch (e: Exception) {
                println("Attempt ${attempt + 1} failed: ${e.message}")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return try {
            block()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun batchRequests(urls: List<String>): List<String> = coroutineScope {
        urls.map { url ->
            async(Dispatchers.IO) {
                getCachedOrFetch(url)
            }
        }.awaitAll()
    }

    private suspend fun getCachedOrFetch(url: String): String {
        val cached = requestCache[url]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTimeout) {
            return cached.data
        }

        val deferred = CompletableDeferred<String>()
        requestQueue.send(NetworkRequest(url, deferred))
        val result = deferred.await()

        requestCache[url] = CachedResponse(result, System.currentTimeMillis())
        return result
    }

    private suspend fun processRequestQueue() {
        while (true) {
            val request = requestQueue.receive()

            while (activeRequests.get() >= maxConcurrentRequests) {
                delay(50)
            }

            GlobalScope.launch(Dispatchers.IO) {
                activeRequests.incrementAndGet()
                try {
                    val result = performRequest(request.url)
                    request.completion.complete(result)
                } catch (e: Exception) {
                    request.completion.completeExceptionally(e)
                } finally {
                    activeRequests.decrementAndGet()
                }
            }
        }
    }

    private suspend fun performRequest(url: String): String {
        return withContext(Dispatchers.IO) {
            "Response from $url"
        }
    }

    fun clearCache() {
        requestCache.clear()
    }

    fun <T> Flow<T>.throttle(periodMillis: Long): Flow<T> = flow {
        var lastTime = 0L
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTime >= periodMillis) {
                emit(value)
                lastTime = currentTime
            }
        }
    }
}