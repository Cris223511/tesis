package com.example.serious_game_usil.presentation.ui.emotion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.repository.EmotionRepository
import com.example.serious_game_usil.data.emotion.EmotionType
import com.example.serious_game_usil.guards.AuthManager
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class EmotionAnalysisActivity : AppCompatActivity() {

    private val emotionRepository = EmotionRepository.getInstance()
    private var selectedImageBitmap: Bitmap? = null

    // Launcher para seleccionar imagen de galería
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageUri(it) }
    }

    // Launcher para tomar foto con cámara
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { handleImageBitmap(it) }
    }

    // Launcher para solicitar permisos de cámara
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emotion_analysis)

        setupToolbar()
        setupClickListeners()
        checkUserPermissions()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Análisis de Emociones"
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_select_gallery).setOnClickListener {
            selectFromGallery()
        }

        findViewById<View>(R.id.btn_take_photo).setOnClickListener {
            checkCameraPermissionAndTakePhoto()
        }

        findViewById<View>(R.id.btn_analyze).setOnClickListener {
            analyzeEmotion()
        }
    }

    private fun checkUserPermissions() {
        val userRoles = AuthManager.getUserRoles()
        Log.d("EmotionAnalysis", "User roles: $userRoles")

        if (!EmotionRepository.canUserEdit()) {
            Toast.makeText(
                this,
                "Tu rol no tiene permisos para realizar análisis de emociones",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun selectFromGallery() {
        selectImageLauncher.launch("image/*")
    }

    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        takePictureLauncher.launch(null)
    }

    private fun handleImageUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            handleImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("EmotionAnalysis", "Error processing image URI", e)
            Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImageBitmap(bitmap: Bitmap) {
        selectedImageBitmap = bitmap

        // Mostrar la imagen seleccionada
        findViewById<android.widget.ImageView>(R.id.iv_selected_image).apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }

        // Habilitar botón de análisis
        findViewById<View>(R.id.btn_analyze).isEnabled = true

        Toast.makeText(this, "Imagen seleccionada correctamente", Toast.LENGTH_SHORT).show()
    }

    private fun analyzeEmotion() {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Por favor selecciona una imagen primero", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostrar loading
        showLoading(true)

        lifecycleScope.launch {
            try {
                // Convertir bitmap a Base64
                val base64Image = bitmapToBase64(bitmap)

                // Llamar al servicio de emociones
                val userRoles = AuthManager.getUserRoles()
                val sessionDescription = when {
                    userRoles.any { it.lowercase() in listOf("cuidador", "terapeuta") } ->
                        "Sesión terapéutica - Análisis emocional por ${AuthManager.getNombresApellidos()}"
                    else ->
                        "Análisis emocional personal - ${AuthManager.getNombresApellidos()}"
                }

                val result = emotionRepository.analyzeEmotion(
                    imageBase64 = base64Image,
                    description = sessionDescription,
                    childId = getChildIdIfApplicable()
                )

                result.fold(
                    onSuccess = { analysisResponse ->
                        showLoading(false)
                        displayAnalysisResults(analysisResponse)
                    },
                    onFailure = { error ->
                        showLoading(false)
                        handleAnalysisError(error)
                    }
                )

            } catch (e: Exception) {
                showLoading(false)
                Log.e("EmotionAnalysis", "Error during analysis", e)
                Toast.makeText(
                    this@EmotionAnalysisActivity,
                    "Error interno: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun getChildIdIfApplicable(): Int? {
        // Si el usuario es cuidador/terapeuta, registrar para el niño bajo su cuidado
        val userRoles = AuthManager.getUserRoles()
        val userId = AuthManager.getUserId()

        return when {
            userRoles.any { it.lowercase() in listOf("cuidador", "terapeuta") } -> {
                // En un sistema real, aquí podrías mostrar un selector de niños
                // Por ahora, usamos el ID del usuario como placeholder
                userId
            }
            else -> null // Análisis para el usuario actual
        }
    }

    private fun displayAnalysisResults(analysis: com.example.serious_game_usil.data.emotion.EmotionAnalysisResponse) {
        val userRoles = AuthManager.getUserRoles()
        val isTherapeuticSession = userRoles.any { it.lowercase() in listOf("cuidador", "terapeuta") }

        val resultsText = buildString {
            if (isTherapeuticSession) {
                appendLine("=== SESIÓN TERAPÉUTICA REGISTRADA ===")
                appendLine("📋 ID de Sesión: ${analysis.id}")
                appendLine("👨‍⚕️ Realizada por: ${AuthManager.getNombresApellidos()}")
                appendLine("📅 Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("🎯 Emoción principal detectada: ${analysis.dominantEmotion}")
                appendLine("📊 Nivel de confianza: ${String.format("%.2f%%", analysis.confidenceScore * 100)}")
                appendLine()
                appendLine("=== DETALLES DEL ANÁLISIS ===")
            } else {
                appendLine("=== ANÁLISIS EMOCIONAL PERSONAL ===")
                appendLine("ID: ${analysis.id}")
                appendLine("Emoción dominante: ${analysis.dominantEmotion}")
                appendLine("Confianza: ${String.format("%.2f%%", analysis.confidenceScore * 100)}")
                appendLine()
            }

            analysis.results.forEachIndexed { index, result ->
                appendLine("--- ${if (isTherapeuticSession) "Evaluación" else "Rostro"} ${index + 1} ---")
                appendLine("Emoción principal: ${result.dominantEmotion}")
                appendLine("Niveles emocionales detectados:")

                val emotions = result.emotions
                listOf(
                    "😊 Alegría" to emotions.happy,
                    "😢 Tristeza" to emotions.sad,
                    "😡 Ira" to emotions.angry,
                    "😮 Sorpresa" to emotions.surprise,
                    "😐 Neutral" to emotions.neutral,
                    "😨 Miedo" to emotions.fear,
                    "😖 Disgusto" to emotions.disgust
                ).sortedByDescending { it.second }.forEach { (name, score) ->
                    val indicator = when {
                        score > 0.7 -> "🔴"
                        score > 0.4 -> "🟡"
                        else -> "🟢"
                    }
                    appendLine("  $indicator $name: ${String.format("%.2f%%", score * 100)}")
                }
                appendLine()
            }

            if (isTherapeuticSession) {
                appendLine("=== NOTAS DE LA SESIÓN ===")
                appendLine("✅ Sesión registrada exitosamente en el sistema")
                appendLine("📈 Los datos se incluirán en el seguimiento terapéutico")
                appendLine("📊 Disponible para reportes de progreso")
                appendLine()
            }

            if (analysis.canEdit) {
                appendLine("✏️ Puedes editar este análisis")
            }
            if (analysis.canDelete) {
                appendLine("🗑️ Puedes eliminar este análisis")
            }
        }

        findViewById<android.widget.TextView>(R.id.tv_results).apply {
            text = resultsText
            visibility = View.VISIBLE
        }

        // Scroll hacia los resultados
        findViewById<android.widget.ScrollView>(R.id.scroll_view)?.post {
            findViewById<android.widget.ScrollView>(R.id.scroll_view)?.fullScroll(View.FOCUS_DOWN)
        }

        val successMessage = if (isTherapeuticSession) {
            "Sesión terapéutica registrada exitosamente"
        } else {
            "Análisis completado exitosamente"
        }
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
    }

    private fun handleAnalysisError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("límite") == true -> {
                "Has alcanzado el límite de análisis diarios"
            }
            error.message?.contains("Token") == true -> {
                "Error de autenticación. Por favor inicia sesión nuevamente"
            }
            error.message?.contains("permisos") == true -> {
                "No tienes permisos para realizar análisis"
            }
            else -> {
                "Error al analizar la imagen: ${error.message}"
            }
        }

        findViewById<android.widget.TextView>(R.id.tv_results).apply {
            text = "❌ $errorMessage"
            visibility = View.VISIBLE
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.progress_bar).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_analyze).isEnabled = !show
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}