package com.example.serious_game_usil.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImageHandler {
    companion object {
        private const val MAX_IMAGE_SIZE = 1024
        private const val COMPRESSION_QUALITY = 85
        
        fun uriToBase64(context: Context, imageUri: Uri): String? {
            return try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = processBitmap(inputStream)
                bitmap?.let { bitmapToBase64(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            val resizedBitmap = resizeBitmap(bitmap)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            val byteArray = outputStream.toByteArray()
            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }
        
        fun base64ToBitmap(base64String: String): Bitmap? {
            return try {
                val cleanedBase64 = if (base64String.contains("base64,")) {
                    base64String.substring(base64String.indexOf("base64,") + 7)
                } else {
                    base64String
                }
                val decodedBytes = Base64.decode(cleanedBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        private fun processBitmap(inputStream: InputStream?): Bitmap? {
            inputStream ?: return null
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            val bytes = inputStream.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            options.apply {
                inSampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
                inJustDecodeBounds = false
            }
            
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
        
        private fun resizeBitmap(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            
            if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
                return bitmap
            }
            
            val ratio = Math.min(
                MAX_IMAGE_SIZE.toFloat() / width,
                MAX_IMAGE_SIZE.toFloat() / height
            )
            
            val newWidth = (width * ratio).toInt()
            val newHeight = (height * ratio).toInt()
            
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1
            
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                
                while ((halfHeight / inSampleSize) >= reqHeight &&
                       (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            
            return inSampleSize
        }
        
        fun getImageSizeInMB(base64String: String): Float {
            val cleanedBase64 = if (base64String.contains("base64,")) {
                base64String.substring(base64String.indexOf("base64,") + 7)
            } else {
                base64String
            }
            val decodedBytes = Base64.decode(cleanedBase64, Base64.DEFAULT)
            return decodedBytes.size / (1024f * 1024f)
        }
        
        fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        }
        
        private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix().apply {
                postRotate(degrees)
            }
            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        }
    }
}