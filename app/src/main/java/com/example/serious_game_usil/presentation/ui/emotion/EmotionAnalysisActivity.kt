package com.example.serious_game_usil.presentation.ui.emotion

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.emotion.EmotionType
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.repository.EmotionRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmotionAnalysisActivity : AppCompatActivity() {

    private val emotionRepository = EmotionRepository.getInstance()
    private var selectedImageBitmap: Bitmap? = null
    private var selectedImageUri: Uri? = null
    private var sessionId: Int? = null
    private var patientId: Int? = null
    private var currentPhotoUri: Uri? = null
    private var lastAnalysisSummary: String? = null
    private var currentAnalysisId: String? = null

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageUri(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            currentPhotoUri?.let { handleImageUri(it, shouldPersistCopy = true) }
        } else {
            Toast.makeText(this, "No se pudo capturar la foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val editImageLauncher = registerForActivityResult(
        StartActivityForResult()
    ) {
        selectedImageUri?.let { uri ->
            handleImageUri(uri)
        }
    }

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

        sessionId = intent.getIntExtra("session_id", -1).takeIf { it > 0 }
        patientId = intent.getIntExtra("patient_id", -1).takeIf { it > 0 }

        setupToolbar()
        setupClickListeners()
        checkUserPermissions()
        preloadExistingAnalysis()
    }

    private fun hasTherapeuticRole(userRoles: List<String>): Boolean {
        return userRoles.any {
            it.lowercase() in listOf(
                "cuidador",
                "padre",
                "pd",
                "responsable",
                "terapeuta",
                "tr",
                "admin",
                "administrador",
                "ad"
            )
        }
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

        findViewById<View>(R.id.btn_save_image).setOnClickListener {
            saveCurrentImage()
        }

        findViewById<View>(R.id.btn_edit_image).setOnClickListener {
            editCurrentImage()
        }

        findViewById<View>(R.id.btnDeleteAnalysis).setOnClickListener {
            confirmDeleteAnalysis()
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
        runCatching {
            createTempCameraImageUri()
        }.onSuccess { uri ->
            currentPhotoUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure { error ->
            Toast.makeText(this, "Error al abrir la cámara: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleImageUri(uri: Uri, shouldPersistCopy: Boolean = false) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            handleImageBitmap(bitmap, originalUri = uri, shouldPersistCopy = shouldPersistCopy)
        } catch (e: Exception) {
            Log.e("EmotionAnalysis", "Error processing image URI", e)
            Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImageBitmap(
        bitmap: Bitmap,
        originalUri: Uri? = null,
        shouldPersistCopy: Boolean = false
    ) {
        selectedImageBitmap = bitmap
        selectedImageUri = if (shouldPersistCopy || originalUri == null) {
            saveImageToGallery(bitmap) ?: originalUri
        } else {
            originalUri
        }

        findViewById<android.widget.ImageView>(R.id.iv_selected_image).apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }

        findViewById<View>(R.id.btn_analyze).isEnabled = true
        findViewById<View>(R.id.image_actions_container).visibility = View.VISIBLE

        val message = if (selectedImageUri != null) {
            "Imagen lista y guardada correctamente"
        } else {
            "Imagen seleccionada correctamente"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun preloadExistingAnalysis() {
        val sessionId = sessionId ?: return

        lifecycleScope.launch {
            emotionRepository.getSessionAnalysis(sessionId).onSuccess { analysis ->
                decodeBase64Bitmap(analysis.image)?.let { bitmap ->
                    handleImageBitmap(bitmap, shouldPersistCopy = false)
                }
                displayAnalysisResults(analysis, isPreloaded = true)
            }
        }
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
                    hasTherapeuticRole(userRoles) ->
                        "Sesión terapéutica - Análisis emocional por ${AuthManager.getNombresApellidos()}"
                    else ->
                        "Análisis emocional personal - ${AuthManager.getNombresApellidos()}"
                }

                val result = emotionRepository.analyzeEmotion(
                    imageBase64 = base64Image,
                    description = sessionDescription,
                    childId = getChildIdIfApplicable(),
                    sessionId = sessionId
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

    private fun createTempCameraImageUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("No se encontró el almacenamiento de imágenes")
        val photoFile = File.createTempFile("EMOTION_${timestamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
    }

    private fun saveImageToGallery(bitmap: Bitmap): Uri? {
        return runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "emotion_analysis_$timestamp.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/SeriousGameUSIL/EmotionAnalysis"
                )
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IllegalStateException("No se pudo guardar la imagen")
                }
            } ?: throw IllegalStateException("No se pudo abrir el destino de la imagen")

            uri
        }.onFailure { error ->
            Log.e("EmotionAnalysis", "No se pudo guardar la imagen del analisis", error)
        }.getOrNull()
    }

    private fun saveCurrentImage() {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Primero selecciona una foto", Toast.LENGTH_SHORT).show()
            return
        }

        val savedUri = saveImageToGallery(bitmap)
        if (savedUri != null) {
            selectedImageUri = savedUri
            Toast.makeText(this, "Foto guardada en Pictures/SeriousGameUSIL/EmotionAnalysis", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No se pudo guardar la foto", Toast.LENGTH_LONG).show()
        }
    }

    private fun editCurrentImage() {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Primero selecciona una foto", Toast.LENGTH_SHORT).show()
            return
        }

        val imageUri = saveImageToGallery(bitmap)
        if (imageUri == null) {
            Toast.makeText(this, "No se pudo preparar la foto para edición", Toast.LENGTH_LONG).show()
            return
        }

        selectedImageUri = imageUri

        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(imageUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        try {
            editImageLauncher.launch(Intent.createChooser(editIntent, "Editar foto"))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "No hay editor disponible. Puedes cambiar la foto desde Cámara o Galería.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getChildIdIfApplicable(): Int? {
        patientId?.let { return it }

        // Si el usuario es cuidador/terapeuta, registrar para el niño bajo su cuidado
        val userRoles = AuthManager.getUserRoles()
        val userId = AuthManager.getUserId()

        return when {
            hasTherapeuticRole(userRoles) -> {
                // En un sistema real, aquí podrías mostrar un selector de niños
                // Por ahora, usamos el ID del usuario como placeholder
                userId
            }
            else -> null // Análisis para el usuario actual
        }
    }

    private fun displayAnalysisResults(
        analysis: com.example.serious_game_usil.data.emotion.EmotionAnalysisResponse,
        isPreloaded: Boolean = false
    ) {
        val userRoles = AuthManager.getUserRoles()
        val isTherapeuticSession = hasTherapeuticRole(userRoles)
        val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val dominantEmotion = formatEmotionLabel(analysis.dominantEmotion)
        val confidenceText = String.format(Locale.getDefault(), "%.2f%%", analysis.confidenceScore * 100)

        val resultsText = buildString {
            if (isTherapeuticSession) {
                appendLine("SESIÓN TERAPÉUTICA REGISTRADA")

                appendLine("🗂️ Sesión terapéutica asociada: ${analysis.sessionId ?: sessionId ?: "No especificada"}")
                appendLine("👨‍⚕️ Realizada por: ${AuthManager.getNombresApellidos()}")
                appendLine("📅 Fecha: $formattedDate")
                appendLine("🎯 Emoción principal detectada: $dominantEmotion")
                appendLine("📊 Nivel de confianza: $confidenceText")
                appendLine()
                appendLine("DETALLES DEL ANÁLISIS ")
            } else {
                appendLine("=== ANÁLISIS EMOCIONAL PERSONAL ===")
                appendLine("ID: ${analysis.id}")
                appendLine("Emoción dominante: $dominantEmotion")
                appendLine("Confianza: $confidenceText")
                appendLine()
            }

            analysis.results.forEachIndexed { index, result ->
                appendLine("--- ${if (isTherapeuticSession) "Evaluación" else "Rostro"} ${index + 1} ---")
                appendLine("Emoción principal: ${formatEmotionLabel(result.dominantEmotion)}")
                appendLine("Niveles emocionales detectados:")

                val emotions = result.emotions
                listOf(
                    "😊 Alegría" to emotions.happy,
                    "😢 Tristeza" to emotions.sad,
                    "😡 Ira" to emotions.angry,
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



            if (isPreloaded) {
                appendLine("ℹ️ Este análisis ya estaba guardado y pertenece a la sesión indicada arriba.")
                appendLine()
            }

        }

        currentAnalysisId = analysis.id
        lastAnalysisSummary = resultsText
        if (!isPreloaded) {
            saveAnalysisSummary(resultsText, analysis.id)
        }

        renderAnalysisSummary(
            analysis = analysis,
            dominantEmotion = dominantEmotion,
            confidenceText = confidenceText,
            formattedDate = formattedDate,
            isTherapeuticSession = isTherapeuticSession,
            isPreloaded = isPreloaded
        )

        findViewById<TextView>(R.id.tv_results).apply {
            text = resultsText
            visibility = View.VISIBLE
        }

        // Scroll hacia los resultados
        findViewById<android.widget.ScrollView>(R.id.scroll_view)?.post {
            findViewById<android.widget.ScrollView>(R.id.scroll_view)?.fullScroll(View.FOCUS_DOWN)
        }

        val successMessage = if (isTherapeuticSession) {
            if (isPreloaded) "Análisis emocional cargado desde la sesión" else "Sesión terapéutica registrada exitosamente"
        } else {
            if (isPreloaded) "Análisis emocional cargado" else "Análisis completado exitosamente"
        }
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
    }

    private fun renderAnalysisSummary(
        analysis: com.example.serious_game_usil.data.emotion.EmotionAnalysisResponse,
        dominantEmotion: String,
        confidenceText: String,
        formattedDate: String,
        isTherapeuticSession: Boolean,
        isPreloaded: Boolean
    ) {
        findViewById<View>(R.id.result_summary_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_result_session).text =
            "Sesion asociada: ${analysis.sessionId ?: sessionId ?: "Sin sesion"}"
        findViewById<TextView>(R.id.tv_result_emotion).text = dominantEmotion
        findViewById<TextView>(R.id.tv_result_confidence).text = "Confianza detectada: $confidenceText"
        findViewById<TextView>(R.id.tv_result_date).text =
            if (isPreloaded) "Analisis recuperado el $formattedDate" else "Analisis generado el $formattedDate"
        findViewById<TextView>(R.id.tv_result_status).text =
            if (isPreloaded) "Guardado" else "Nuevo"
        findViewById<TextView>(R.id.tv_result_actor).text =
            if (isTherapeuticSession) AuthManager.getNombresApellidos() else "Analisis personal"

        val emotionBarsContainer = findViewById<LinearLayout>(R.id.emotion_bars_container)
        emotionBarsContainer.removeAllViews()
        findViewById<View>(R.id.result_actions_container).visibility =
            if (analysis.canDelete) View.VISIBLE else View.GONE

        val topResult = analysis.results.firstOrNull()
        if (topResult != null) {
            val sortedEmotions = listOf(
                "Alegria" to topResult.emotions.happy,
                "Tristeza" to topResult.emotions.sad,
                "Enojo" to topResult.emotions.angry,
                "Miedo" to topResult.emotions.fear,
                "Disgusto" to topResult.emotions.disgust
            ).sortedByDescending { it.second }

            sortedEmotions.forEach { (label, score) ->
                val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, emotionBarsContainer, false)
                row.findViewById<TextView>(android.R.id.text1).apply {
                    text = label
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@EmotionAnalysisActivity, R.color.primary_dark))
                }
                row.findViewById<TextView>(android.R.id.text2).apply {
                    text = String.format(Locale.getDefault(), "%.2f%%", score * 100)
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@EmotionAnalysisActivity, R.color.text_secondary))
                }
                emotionBarsContainer.addView(row)
            }
        }
    }

    private fun formatEmotionLabel(raw: String?): String {
        return when (raw?.lowercase(Locale.getDefault())) {
            "happy" -> "Alegria"
            "sad" -> "Tristeza"
            "angry" -> "Enojo"
            "fear" -> "Miedo"
            "disgust" -> "Disgusto"
            else -> raw?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                ?: "Sin dato"
        }
    }

    private fun decodeBase64Bitmap(imageData: String?): Bitmap? {
        if (imageData.isNullOrBlank()) return null
        return runCatching {
            val sanitized = imageData.substringAfter("base64,", imageData)
            val bytes = Base64.decode(sanitized, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun saveAnalysisSummary(summary: String, analysisId: String) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "emotion_result_${analysisId}_$timestamp.txt")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOCUMENTS}/SeriousGameUSIL/EmotionAnalysis"
                )
            }

            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IllegalStateException("No se pudo crear el archivo de resultados")

            contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                writer?.write(summary)
            }
        }.onFailure { error ->
            Log.e("EmotionAnalysis", "No se pudo guardar el resumen del analisis", error)
            Toast.makeText(this, "No se pudo guardar el resumen del análisis", Toast.LENGTH_LONG).show()
        }.onSuccess {
            Toast.makeText(this, "Resultados guardados en Documents/SeriousGameUSIL/EmotionAnalysis", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDeleteAnalysis() {
        val analysisId = currentAnalysisId
        if (analysisId.isNullOrBlank()) {
            Toast.makeText(this, "No hay análisis cargado para eliminar", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar análisis")
            .setMessage("Se eliminará el análisis emocional asociado a esta sesión. Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteAnalysis(analysisId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteAnalysis(analysisId: String) {
        showLoading(true)
        lifecycleScope.launch {
            emotionRepository.deleteAnalysis(analysisId).fold(
                onSuccess = {
                    showLoading(false)
                    currentAnalysisId = null
                    findViewById<View>(R.id.result_summary_container).visibility = View.GONE
                    findViewById<View>(R.id.result_actions_container).visibility = View.GONE
                    findViewById<TextView>(R.id.tv_results).apply {
                        text = "El análisis emocional fue eliminado."
                        visibility = View.VISIBLE
                    }
                    Toast.makeText(this@EmotionAnalysisActivity, "Análisis eliminado correctamente", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    showLoading(false)
                    Toast.makeText(
                        this@EmotionAnalysisActivity,
                        error.message ?: "No se pudo eliminar el análisis",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
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
