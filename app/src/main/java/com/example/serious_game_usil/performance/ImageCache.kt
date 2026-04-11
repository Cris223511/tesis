package com.example.serious_game_usil.performance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ImageCache(context: Context) {
    private val memoryCache: LruCache<String, Bitmap>
    private val diskCacheDir: File = File(context.cacheDir, "images")

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8

        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs()
        }
    }

    suspend fun getBitmap(key: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(key)?.let { return@withContext it }

        val diskFile = File(diskCacheDir, hashKey(key))
        if (diskFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
            bitmap?.let {
                memoryCache.put(key, it)
                return@withContext it
            }
        }
        null
    }

    suspend fun putBitmap(key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        memoryCache.put(key, bitmap)

        val diskFile = File(diskCacheDir, hashKey(key))
        try {
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun hashKey(key: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(key.toByteArray())
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }

    fun getCacheSize(): Long {
        return diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}