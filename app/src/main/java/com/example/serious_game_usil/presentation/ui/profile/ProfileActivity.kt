package com.example.serious_game_usil.presentation.ui.administrador.profile

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserProfileResponse
import com.example.serious_game_usil.databinding.ActivityProfileBinding
import com.example.serious_game_usil.presentation.ui.administrador.list.EditUserActivity
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.utils.ImageHandler
import com.example.serious_game_usil.`interface`.UploadBannerResponse
import com.example.serious_game_usil.`interface`.BannerChangesResponse
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var changePhotoButton: FloatingActionButton
    private lateinit var changePasswordButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private var bannerImage: ImageView? = null
    private var currentUserId: Int = -1
    private var photoChangesRemaining = 2
    private var bannerChangesRemaining = 2
    private var profileChangesRemaining = 2
    private var currentProfile: UserProfileResponse? = null
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            if (isUploadingBanner) {
                processSelectedImageForBanner(it)
            } else {
                processSelectedImage(it)
            }
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { 
                if (isUploadingBanner) {
                    processSelectedImageForBanner(it)
                } else {
                    processSelectedImage(it)
                }
            }
        }
    }
    
    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private var isUploadingBanner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setupViewModel()
        initViews()
        setupObservers()

        currentUserId = intent.getIntExtra("USER_ID", -1)
        if (currentUserId > 0) {
            viewModel.loadUserProfile(currentUserId)
        } else {
            viewModel.loadCurrentUserProfile()
        }
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = ProfileViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]
    }

    private fun initViews() {
        findViewById<MaterialToolbar>(R.id.toolBar).setNavigationOnClickListener { finish() }

        changePhotoButton = findViewById(R.id.changePhotoButton)
        changePasswordButton = findViewById(R.id.changePasswordButton)
        progressBar = findViewById(R.id.progressBar) ?: ProgressBar(this)
        
        try {
            bannerImage = findViewById(R.id.bannerImage)
            bannerImage?.setOnClickListener {
                checkBannerChangesAndShowOptions()
            }
        } catch (e: Exception) {
            Log.w("ProfileActivity", "Banner image not found in layout: ${e.message}")
            bannerImage = null
        }

        changePhotoButton.setOnClickListener {
            checkPhotoChangesAndShowOptions()
        }

        changePasswordButton.setOnClickListener {
            val intent = Intent(this, com.example.serious_game_usil.presentation.ui.password.ValidateEmailActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.updateButton).setOnClickListener {
            checkProfileChangesAndShowDialog()
        }

        findViewById<MaterialButton>(R.id.viewChildrenButton).setOnClickListener {
            viewModel.loadUserChildren()
        }
    }

    private fun setupObservers() {
        viewModel.profileData.observe(this) { profile ->
            profile?.let { updateUI(it) }
        }
        
        viewModel.photoUploadResult.observe(this) { result ->
            result?.let {
                progressBar.visibility = View.GONE
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Foto actualizada correctamente (${it.changes_remaining} cambios restantes)",
                    Snackbar.LENGTH_LONG
                ).show()
                photoChangesRemaining = it.changes_remaining
                viewModel.clearPhotoUploadResult()
                // Recargar el perfil para mostrar la nueva foto
                if (currentUserId > 0) {
                    viewModel.loadUserProfile(currentUserId)
                } else {
                    viewModel.loadCurrentUserProfile()
                }
            }
        }
        
        viewModel.photoChanges.observe(this) { changes ->
            changes?.let {
                photoChangesRemaining = it.changes_remaining
            }
        }
        
        viewModel.isUploadingPhoto.observe(this) { isUploading ->
            progressBar.visibility = if (isUploading) View.VISIBLE else View.GONE
            changePhotoButton.isEnabled = !isUploading
        }
        
        viewModel.bannerUploadResult.observe(this) { result: UploadBannerResponse? ->
            result?.let {
                progressBar.visibility = View.GONE
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Banner actualizado correctamente (${it.changes_remaining} cambios restantes)",
                    Snackbar.LENGTH_LONG
                ).show()
                bannerChangesRemaining = it.changes_remaining
                viewModel.clearBannerUploadResult()
                // Recargar el perfil para mostrar el nuevo banner
                if (currentUserId > 0) {
                    viewModel.loadUserProfile(currentUserId)
                } else {
                    viewModel.loadCurrentUserProfile()
                }
            }
        }
        
        viewModel.bannerChanges.observe(this) { changes: BannerChangesResponse? ->
            changes?.let {
                bannerChangesRemaining = it.changes_remaining
            }
        }
        
        viewModel.isUploadingBanner.observe(this) { isUploading ->
            progressBar.visibility = if (isUploading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                progressBar.visibility = View.GONE
                Snackbar.make(
                    findViewById(android.R.id.content),
                    it,
                    Snackbar.LENGTH_LONG
                ).setAction("Reintentar") {
                    // No hacer nada específico
                }.show()
                viewModel.clearError()
            }
        }

        viewModel.childrenData.observe(this) { children ->
            children?.let {
                Toast.makeText(this, "Tiene ${it.total} niños registrados", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                if (it.contains("No autorizado", ignoreCase = true) ||
                    it.contains("permisos", ignoreCase = true)) {
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            findViewById<ProgressBar>(R.id.progressBar).visibility =
                if (isLoading) View.VISIBLE else View.GONE
            findViewById<MaterialButton>(R.id.updateButton).isEnabled = !isLoading
        }

        viewModel.profileUpdateResult.observe(this) { result ->
            result?.let {
                val changesLeft = profileChangesRemaining - 1
                val message = if (changesLeft > 0) {
                    "Perfil actualizado correctamente ($changesLeft cambios restantes)"
                } else {
                    "Perfil actualizado correctamente (límite alcanzado)"
                }
                
                Snackbar.make(
                    findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.clearProfileUpdateResult()
                
                // Recargar cambios para actualizar el contador
                viewModel.checkProfileChanges()
            }
        }

        viewModel.profileChanges.observe(this) { changes ->
            changes?.let {
                profileChangesRemaining = it.changes_remaining
            }
        }
    }

    private fun updateUI(profile: UserProfileResponse) {
        currentProfile = profile
        findViewById<TextView>(R.id.nameText).text = profile.nombresApellidos
        findViewById<TextView>(R.id.usernameText).text = "@${profile.usuario}"

        val statusChipActive = findViewById<Chip>(R.id.statusChipActive)
        val statusChipInactive = findViewById<Chip>(R.id.statusChipInactive)

        // Lógica invertida: activo = false (0) significa ACTIVO, activo = true (1) significa INACTIVO
        if (!profile.activo) {  // Si es false (0) = Usuario ACTIVO
            statusChipActive.visibility = View.VISIBLE
            statusChipInactive.visibility = View.GONE
        } else {  // Si es true (1) = Usuario INACTIVO
            statusChipActive.visibility = View.GONE
            statusChipInactive.visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.phoneText).text = profile.telefono ?: "Sin registrar"
        findViewById<TextView>(R.id.emailText).text = profile.correo

        val docType = when(profile.tipoDocumento) {
            "DNI" -> "DNI"
            "CE" -> "CE"
            else -> "Doc"
        }
        findViewById<TextView>(R.id.dniText).text = "$docType: ${profile.numDocumento}"

        findViewById<TextView>(R.id.birthdateText).text = profile.fechaNacimiento ?: "No especificado"

        findViewById<TextView>(R.id.genderText).text = when(profile.sexo) {
            "M", "Masculino" -> "Masculino"
            "F", "Femenino" -> "Femenino"
            else -> "No especificado"
        }

        val childrenCountCard = findViewById<CardView>(R.id.childrenCountCard)
        val viewChildrenButton = findViewById<MaterialButton>(R.id.viewChildrenButton)
        val childrenCountText = findViewById<TextView>(R.id.childrenCountText)

        val isPadre = profile.roles?.contains("Padre") == true
        if (isPadre && profile.childrenCount > 0) {
            childrenCountCard.visibility = View.VISIBLE
            viewChildrenButton.visibility = View.VISIBLE
            childrenCountText.text = "Niños: ${profile.childrenCount}"
        } else {
            childrenCountCard.visibility = View.GONE
            viewChildrenButton.visibility = View.GONE
        }

        val isHijo = profile.roles?.contains("Hijo") == true
        val parentInfoCard = findViewById<MaterialCardView>(R.id.parentInfoCard)
        val parentInfoLayout = findViewById<LinearLayout>(R.id.parentInfoLayout)

        if (isHijo && !profile.parentInfo.isNullOrEmpty()) {
            parentInfoCard.visibility = View.VISIBLE
            parentInfoLayout.removeAllViews()

            profile.parentInfo.forEach { parent ->
                val parentView = LayoutInflater.from(this).inflate(
                    android.R.layout.simple_list_item_2,
                    parentInfoLayout,
                    false
                )
                parentView.findViewById<TextView>(android.R.id.text1).text = parent.nombresApellidos
                parentView.findViewById<TextView>(android.R.id.text2).text =
                    "${parent.correo} • ${parent.telefono}"
                parentInfoLayout.addView(parentView)
            }
        } else {
            parentInfoCard.visibility = View.GONE
        }

        val descriptionCard = findViewById<MaterialCardView>(R.id.descriptionCard)
        val descriptionText = findViewById<TextView>(R.id.descriptionText)

        if (profile.descripcion.isNullOrEmpty()) {
            descriptionCard.visibility = View.GONE
        } else {
            descriptionCard.visibility = View.VISIBLE
            descriptionText.text = profile.descripcion
        }

        // Manejar la foto del perfil
        val avatarImage = findViewById<ImageView>(R.id.avatarImage)
        
        if (!profile.foto.isNullOrEmpty()) {
            Log.d("ProfileActivity", "Mostrando foto del usuario, tamaño: ${profile.foto.length} caracteres")
            try {
                val cleanBase64 = if (profile.foto.contains("base64,")) {
                    profile.foto.substring(profile.foto.indexOf("base64,") + 7)
                } else {
                    profile.foto
                }
                
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                if (bitmap != null) {
                    avatarImage.setImageBitmap(bitmap)
                    Log.d("ProfileActivity", "Foto cargada exitosamente")
                } else {
                    Log.e("ProfileActivity", "Bitmap es null después de decodificar")
                    avatarImage.setImageResource(R.drawable.ic_person)
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error al decodificar la foto: ${e.message}", e)
                avatarImage.setImageResource(R.drawable.ic_person)
            }
        } else {
            Log.d("ProfileActivity", "Usuario sin foto, mostrando avatar por defecto")
            avatarImage.setImageResource(R.drawable.ic_person)
        }
        
        // Manejar el banner del perfil
        bannerImage?.let { bannerImg ->
            if (!profile.banner.isNullOrEmpty()) {
                Log.d("ProfileActivity", "Mostrando banner del usuario, tamaño: ${profile.banner.length} caracteres")
                try {
                    val cleanBase64 = if (profile.banner.contains("base64,")) {
                        profile.banner.substring(profile.banner.indexOf("base64,") + 7)
                    } else {
                        profile.banner
                    }
                    
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    
                    if (bitmap != null) {
                        bannerImg.setImageBitmap(bitmap)
                        bannerImg.scaleType = ImageView.ScaleType.CENTER_CROP
                        // Ocultar el ícono de cámara cuando hay imagen
                        try {
                            findViewById<ImageView>(R.id.bannerCameraIcon)?.visibility = View.GONE
                        } catch (e: Exception) {
                            // Ignorar si no existe el ícono
                        }
                        Log.d("ProfileActivity", "Banner cargado exitosamente")
                    } else {
                        Log.e("ProfileActivity", "Bitmap de banner es null después de decodificar")
                        bannerImg.setImageResource(android.R.color.transparent)
                        // Mostrar el ícono de cámara cuando no hay imagen
                        try {
                            findViewById<ImageView>(R.id.bannerCameraIcon)?.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            // Ignorar si no existe el ícono
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Error al decodificar el banner: ${e.message}", e)
                    bannerImg.setImageResource(android.R.color.transparent)
                    // Mostrar el ícono de cámara cuando hay error
                    try {
                        findViewById<ImageView>(R.id.bannerCameraIcon)?.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        // Ignorar si no existe el ícono
                    }
                }
            } else {
                Log.d("ProfileActivity", "Usuario sin banner, mostrando fondo azul")
                bannerImg.setImageResource(R.drawable.header_gradient)
                bannerImg.scaleType = ImageView.ScaleType.MATRIX
                // Mostrar el ícono de cámara cuando no hay banner
                try {
                    findViewById<ImageView>(R.id.bannerCameraIcon)?.visibility = View.VISIBLE
                } catch (e: Exception) {
                    // Ignorar si no existe el ícono
                }
            }
        } ?: run {
            Log.d("ProfileActivity", "Banner image view no disponible en el layout")
        }
    }

    private fun checkPhotoChangesAndShowOptions() {
        viewModel.checkPhotoChanges()
        
        if (photoChangesRemaining <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Límite alcanzado")
                .setMessage("Has alcanzado el límite máximo de 2 cambios de foto")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        
        showPhotoOptions()
    }
    
    private fun showPhotoOptions() {
        val options = if (photoChangesRemaining > 0) {
            arrayOf(
                "Tomar foto",
                "Elegir de galería",
                "Ver cambios restantes ($photoChangesRemaining de 2)"
            )
        } else {
            arrayOf("Ver cambios restantes (0 de 2)")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Actualizar foto de perfil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                    2 -> showRemainingChanges()
                }
            }
            .show()
    }

    private fun openGallery() {
        isUploadingBanner = false
        galleryLauncher.launch("image/*")
    }
    
    private fun checkCameraPermissionAndOpen() {
        isUploadingBanner = false
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun launchCamera() {
        try {
            currentPhotoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                currentPhotoFile!!
            )
            cameraLauncher.launch(currentPhotoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            deleteOnExit()
        }
    }
    
    private fun processSelectedImage(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        
        val base64Image = ImageHandler.uriToBase64(this, uri)
        
        if (base64Image != null) {
            val sizeInMB = ImageHandler.getImageSizeInMB(base64Image)
            
            if (sizeInMB > 5) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "La imagen excede el tamaño máximo de 5MB", Toast.LENGTH_LONG).show()
                return
            }
            
            viewModel.uploadPhoto(base64Image)
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun processSelectedImageForBanner(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        
        val base64Image = ImageHandler.uriToBase64(this, uri)
        
        if (base64Image != null) {
            val sizeInMB = ImageHandler.getImageSizeInMB(base64Image)
            
            if (sizeInMB > 5) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "La imagen es muy grande. El tamaño máximo es 5MB", Toast.LENGTH_LONG).show()
                return
            }
            
            viewModel.uploadBanner(base64Image)
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error al procesar la imagen. Intente con otra", Toast.LENGTH_SHORT).show()
        }
        
        isUploadingBanner = false
    }
    
    private fun showRemainingChanges() {
        AlertDialog.Builder(this)
            .setTitle("Cambios de foto")
            .setMessage("Te quedan $photoChangesRemaining de 2 cambios de foto disponibles")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun removePhoto() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar foto")
            .setMessage("Esta acción contará como uno de tus 2 cambios permitidos. ¿Continuar?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.uploadPhoto("")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun checkBannerChangesAndShowOptions() {
        viewModel.checkBannerChanges()
        
        if (bannerChangesRemaining <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Límite alcanzado")
                .setMessage("Has alcanzado el límite máximo de 2 cambios de banner")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        
        showBannerOptions()
    }
    
    private fun showBannerOptions() {
        val options = if (bannerChangesRemaining > 0) {
            arrayOf(
                "Tomar foto con la cámara",
                "Elegir de la galería",
                "Cambios restantes: $bannerChangesRemaining de 2"
            )
        } else {
            arrayOf("Sin cambios disponibles (0 de 2)")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Actualizar banner de perfil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpenForBanner()
                    1 -> openGalleryForBanner()
                    2 -> showRemainingBannerChanges()
                }
            }
            .show()
    }
    
    private fun checkCameraPermissionAndOpenForBanner() {
        isUploadingBanner = true
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCameraForBanner()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun launchCameraForBanner() {
        try {
            currentPhotoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                currentPhotoFile!!
            )
            cameraLauncher.launch(currentPhotoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            isUploadingBanner = false
            Toast.makeText(this, "Error al abrir la cámara. Por favor, intente nuevamente.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGalleryForBanner() {
        isUploadingBanner = true
        galleryLauncher.launch("image/*")
    }
    
    private fun showRemainingBannerChanges() {
        AlertDialog.Builder(this)
            .setTitle("Cambios disponibles")
            .setMessage("Tienes $bannerChangesRemaining de 2 cambios de banner restantes")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun getUserIdFromPrefs(): Int {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("user_id", -1)
    }
    
    private fun checkProfileChangesAndShowDialog() {
        viewModel.checkProfileChanges()
        
        if (profileChangesRemaining <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Límite alcanzado")
                .setMessage("Has alcanzado el límite máximo de 2 cambios de perfil")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        
        showEditProfileDialog()
    }
    
    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
        
        // Campos de solo lectura
        val editNombres = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNombresApellidos)
        val editFechaNacimiento = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editFechaNacimiento)
        val editSexo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSexo)
        val editTipoDocumento = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTipoDocumento)
        val editNumDocumento = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNumDocumento)
        
        // Campos editables
        val editCorreo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editCorreo)
        val editTelefono = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTelefono)
        
        // Prellenar con datos actuales del perfil
        currentProfile?.let { profile ->
            // Campos de solo lectura
            editNombres.setText(profile.nombresApellidos)
            editFechaNacimiento.setText(profile.fechaNacimiento ?: "")
            
            val sexoTexto = when (profile.sexo?.uppercase()) {
                "M", "MASCULINO" -> "Masculino"
                "F", "FEMENINO" -> "Femenino"
                else -> ""
            }
            editSexo.setText(sexoTexto)
            
            val tipoDocTexto = when (profile.tipoDocumento?.uppercase()) {
                "DNI" -> "DNI"
                "CE" -> "Carné de Extranjería"
                else -> profile.tipoDocumento ?: ""
            }
            editTipoDocumento.setText(tipoDocTexto)
            editNumDocumento.setText(profile.numDocumento ?: "")
            
            // Campos editables
            editCorreo.setText(profile.correo)
            editTelefono.setText(profile.telefono ?: "")
        }
        
        // Crear diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Botón cancelar
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelar).setOnClickListener {
            dialog.dismiss()
            // No redirige, solo cierra el modal
        }
        
        // Botón guardar
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGuardar).setOnClickListener {
            val correo = editCorreo.text.toString().trim()
            val telefono = editTelefono.text.toString().trim()
            
            if (correo.isEmpty()) {
                Toast.makeText(this, "El correo electrónico es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                Toast.makeText(this, "Por favor ingrese un correo válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Actualizar perfil con ViewModel
            viewModel.updateProfile(correo, telefono)
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
}