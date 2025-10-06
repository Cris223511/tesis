package com.example.serious_game_usil.presentation.ui.patients

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.CreatePatientRequest
import com.example.serious_game_usil.data.UpdatePatientRequest
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ActivityCreateEditPatientBinding
import com.example.serious_game_usil.guards.AuthManager
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class CreateEditPatientActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateEditPatientBinding
    private val viewModel: PatientsViewModel by viewModels { PatientsViewModelFactory() }

    private var patientId: Int? = null
    private var isEditMode = false
    private var selectedPhotoBase64: String? = null
    private var caregivers: List<UserListItem> = emptyList()
    private var pendingPatientData: com.example.serious_game_usil.data.Patient? = null

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { handleCameraPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("CreateEditPatientActivity", "onCreate called")

        try {
            binding = ActivityCreateEditPatientBinding.inflate(layoutInflater)
            setContentView(binding.root)

            patientId = intent.getIntExtra("patient_id", -1).takeIf { it != -1 }
            isEditMode = patientId != null

            android.util.Log.d("CreateEditPatientActivity", "PatientId: $patientId, EditMode: $isEditMode")
        } catch (e: Exception) {
            android.util.Log.e("CreateEditPatientActivity", "Error in onCreate", e)
            throw e
        }

        try {
            setupUI()
            setupObservers()
            loadCaregivers()

            if (isEditMode) {
                patientId?.let { loadPatientData(it) }
            }
            android.util.Log.d("CreateEditPatientActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("CreateEditPatientActivity", "Error in onCreate setup", e)
            Toast.makeText(this, "Error al inicializar la pantalla: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEditMode) "Editar paciente" else "Nuevo paciente"

        setupDropdowns()
        setupDatePicker()
        setupButtons()
        setupPhotoSelector()
        setupCaregiverVisibility()
        setupBMICalculation()
    }

    private fun setupDropdowns() {
        // Document types
        val documentTypes = arrayOf("DNI", "Pasaporte", "Carnet de extranjería")
        val documentAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, documentTypes)
        binding.actvDocumentType.setAdapter(documentAdapter)

        // Gender options
        val genderOptions = arrayOf("Masculino", "Femenino")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        binding.actvGender.setAdapter(genderAdapter)
    }

    private fun setupDatePicker() {
        binding.etBirthDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            if (validateForm()) {
                savePatient()
            }
        }
    }

    private fun setupPhotoSelector() {
        binding.btnSelectPhoto.setOnClickListener {
            showPhotoSelectionDialog()
        }
    }

    private fun setupObservers() {
        // Observe patient creation/update
        lifecycleScope.launch {
            viewModel.createPatientResult.collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@CreateEditPatientActivity,
                            "Paciente ${if (isEditMode) "actualizado" else "creado"} exitosamente",
                            Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CreateEditPatientActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CreateEditPatientActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                    null -> {
                        // No action needed for null result
                    }
                }
            }
        }

        // Observe patient data loading
        lifecycleScope.launch {
            viewModel.selectedPatient.collect { result ->
                android.util.Log.d("CreateEditPatientActivity", "Patient data result: $result")
                when (result) {
                    is ApiResult.Success -> {
                        android.util.Log.d("CreateEditPatientActivity", "Patient data loaded successfully: ${result.data}")
                        // Store patient data, populate form after caregivers are loaded
                        populateFormWithData(result.data)
                    }
                    is ApiResult.Error -> {
                        android.util.Log.e("CreateEditPatientActivity", "Error loading patient: ${result.message}")
                        Toast.makeText(this@CreateEditPatientActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        android.util.Log.e("CreateEditPatientActivity", "Network error loading patient: ${result.exception.message}")
                        Toast.makeText(this@CreateEditPatientActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }

                    null -> {
                        android.util.Log.d("CreateEditPatientActivity", "Patient data result is null - initial state")
                        // Estado inicial - no hay datos que cargar
                    }
                }
            }
        }

        // Observe caregivers
        lifecycleScope.launch {
            viewModel.caregivers.collect { result ->
                android.util.Log.d("CreateEditPatient", "Caregivers result: $result")
                when (result) {
                    is ApiResult.Success -> {
                        caregivers = result.data
                        android.util.Log.d("CreateEditPatient", "Setting up dropdown with ${caregivers.size} caregivers")
                        setupCaregiversDropdown()

                        // If we have pending patient data, populate form now
                        pendingPatientData?.let { patient ->
                            android.util.Log.d("CreateEditPatient", "Populating form with pending patient data")
                            populateForm(patient)
                            pendingPatientData = null
                        }
                    }
                    is ApiResult.Error -> {
                        android.util.Log.e("CreateEditPatient", "Error loading caregivers: ${result.message}")
                        Toast.makeText(this@CreateEditPatientActivity, "Error cargando cuidadores: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                    is ApiResult.NetworkError -> {
                        android.util.Log.e("CreateEditPatient", "Network error: ${result.exception.message}")
                        Toast.makeText(this@CreateEditPatientActivity, "Error de conexión: ${result.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    null -> {
                        // Estado inicial - lista vacía de cuidadores
                        android.util.Log.d("CreateEditPatient", "Initial state - empty caregivers")
                        caregivers = emptyList()
                    }
                }
            }
        }
    }

    private fun loadCaregivers() {
        viewModel.loadCaregivers()
    }

    private fun loadPatientData(patientId: Int) {
        android.util.Log.d("CreateEditPatientActivity", "Loading patient data for ID: $patientId")
        viewModel.loadPatient(patientId)
    }

    private fun setupCaregiversDropdown() {
        val caregiverNames = caregivers.map { "${it.nombresApellidos} - ${it.nombreUsuario}" }
        val caregiverAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, caregiverNames)
        binding.actvCaregiver.setAdapter(caregiverAdapter)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        // Calcular fechas límite (18-60 años)
        val today = Calendar.getInstance()
        val maxDate = Calendar.getInstance().apply {
            add(Calendar.YEAR, -18) // Máximo 18 años atrás (mínimo 18 años)
        }
        val minDate = Calendar.getInstance().apply {
            add(Calendar.YEAR, -60) // Mínimo 60 años atrás (máximo 60 años)
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }

                // Validar edad
                if (selectedDate.after(maxDate)) {
                    Toast.makeText(this, "El paciente debe tener al menos 18 años", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                if (selectedDate.before(minDate)) {
                    Toast.makeText(this, "El paciente no puede tener más de 60 años", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                binding.etBirthDate.setText(displayDateFormatter.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR) - 30, // Valor por defecto: 30 años
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Establecer límites en el DatePicker
        datePickerDialog.datePicker.maxDate = maxDate.timeInMillis
        datePickerDialog.datePicker.minDate = minDate.timeInMillis

        datePickerDialog.show()
    }

    private fun showPhotoSelectionDialog() {
        val options = arrayOf("Cámara", "Galería")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Seleccionar foto")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(null)
                    1 -> imagePickerLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = resizeBitmap(bitmap, 300, 300)
            selectedPhotoBase64 = bitmapToBase64(resizedBitmap)
            binding.ivPatientPhoto.setImageBitmap(resizedBitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraPhoto(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, 300, 300)
        selectedPhotoBase64 = bitmapToBase64(resizedBitmap)
        binding.ivPatientPhoto.setImageBitmap(resizedBitmap)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight

        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Name validation
        if (binding.etPatientName.text.isNullOrBlank()) {
            binding.tilPatientName.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilPatientName.error = null
        }

        // Birth date validation
        if (binding.etBirthDate.text.isNullOrBlank()) {
            binding.tilBirthDate.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilBirthDate.error = null
        }

        // Document type validation
        if (binding.actvDocumentType.text.isNullOrBlank()) {
            binding.tilDocumentType.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilDocumentType.error = null
        }

        // Document number validation
        if (binding.etDocumentNumber.text.isNullOrBlank()) {
            binding.tilDocumentNumber.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilDocumentNumber.error = null
        }

        // Gender validation
        if (binding.actvGender.text.isNullOrBlank()) {
            binding.tilGender.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilGender.error = null
        }

        return isValid
    }

    private fun savePatient() {
        val birthDate = try {
            val displayDate = displayDateFormatter.parse(binding.etBirthDate.text.toString())
            dateFormatter.format(displayDate!!)
        } catch (e: Exception) {
            binding.etBirthDate.text.toString()
        }

        val selectedCaregiverIndex = caregivers.indexOfFirst {
            "${it.nombresApellidos} - ${it.nombreUsuario}" == binding.actvCaregiver.text.toString()
        }
        val caregiverId = if (selectedCaregiverIndex >= 0) caregivers[selectedCaregiverIndex].id else null

        // Obtener el ID del usuario actual si es terapeuta
        val userRoles = AuthManager.getUserRoles()
        val isTherapist = userRoles.any { it.lowercase() in listOf("terapeuta", "therapist") }
        val currentUserId = if (isTherapist) AuthManager.getUserId() else null

        if (isEditMode) {
            val updateRequest = UpdatePatientRequest(
                nombresApellidos = binding.etPatientName.text.toString(),
                fechaNacimiento = birthDate,
                tipoDocumento = binding.actvDocumentType.text.toString(),
                numDocumento = binding.etDocumentNumber.text.toString(),
                altura = binding.etHeight.text.toString().toDoubleOrNull(),
                peso = binding.etWeight.text.toString().toDoubleOrNull(),
                sexo = binding.actvGender.text.toString(),
                diagnosticoClinico = binding.etClinicalDiagnosis.text.toString().takeIf { it.isNotBlank() },
                fotoMovil = selectedPhotoBase64,
                cuidadorID = caregiverId,
                activo = null // Keep current status
            )
            viewModel.updatePatient(patientId!!, updateRequest)
        } else {
            val createRequest = CreatePatientRequest(
                nombresApellidos = binding.etPatientName.text.toString(),
                fechaNacimiento = birthDate,
                tipoDocumento = binding.actvDocumentType.text.toString(),
                numDocumento = binding.etDocumentNumber.text.toString(),
                altura = binding.etHeight.text.toString().toDoubleOrNull(),
                peso = binding.etWeight.text.toString().toDoubleOrNull(),
                sexo = binding.actvGender.text.toString(),
                diagnosticoClinico = binding.etClinicalDiagnosis.text.toString().takeIf { it.isNotBlank() },
                fotoMovil = selectedPhotoBase64,
                cuidadorID = caregiverId,
                terapeutaID = currentUserId  // Asignar automáticamente el terapeuta actual
            )
            viewModel.createPatient(createRequest)
        }
    }

    private fun populateFormWithData(patient: com.example.serious_game_usil.data.Patient) {
        android.util.Log.d("CreateEditPatientActivity", "populateFormWithData called - caregivers loaded: ${caregivers.isNotEmpty()}, patient: ${patient.nombresApellidos}")
        pendingPatientData = patient

        if (caregivers.isNotEmpty()) {
            // Both patient data and caregivers are loaded, populate form
            populateForm(patient)
            pendingPatientData = null
        }
        // If caregivers not loaded yet, populateForm will be called when they finish loading
    }

    private fun populateForm(patient: com.example.serious_game_usil.data.Patient) {
        android.util.Log.d("CreateEditPatientActivity", "populateForm called with patient: ${patient.nombresApellidos}")
        binding.etPatientName.setText(patient.nombresApellidos)

        // Convert date format for display
        try {
            val serverDate = dateFormatter.parse(patient.fechaNacimiento)
            binding.etBirthDate.setText(displayDateFormatter.format(serverDate!!))
        } catch (e: Exception) {
            binding.etBirthDate.setText(patient.fechaNacimiento)
        }

        binding.actvDocumentType.setText(patient.tipoDocumento, false)
        binding.etDocumentNumber.setText(patient.numDocumento)
        binding.etHeight.setText(patient.altura?.toString() ?: "")
        binding.etWeight.setText(patient.peso?.toString() ?: "")
        binding.actvGender.setText(patient.sexo, false)
        binding.etClinicalDiagnosis.setText(patient.diagnosticoClinico ?: "")

        // Set caregiver if exists
        patient.cuidadorNombre?.let { caregiverName ->
            val caregiverItem = caregivers.find { it.nombresApellidos == caregiverName }
            caregiverItem?.let {
                binding.actvCaregiver.setText("${it.nombresApellidos} - ${it.nombreUsuario}", false)
            }
        }

        // Load photo if exists
        patient.fotoMovil?.let { base64Photo ->
            try {
                val decodedBytes = Base64.decode(base64Photo, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.ivPatientPhoto.setImageBitmap(bitmap)
                selectedPhotoBase64 = base64Photo
            } catch (e: Exception) {
                // Keep default placeholder
            }
        }

        // Calculate and display BMI if height and weight are available
        calculateAndDisplayBMI()
    }

    private fun setupCaregiverVisibility() {
        try {
            // Solo los administradores pueden asignar cuidadores
            // Los terapeutas pueden crear pacientes sin cuidador
            val userRoles = AuthManager.getUserRoles()
            android.util.Log.d("CreateEditPatientActivity", "User roles: $userRoles")
            val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }
            android.util.Log.d("CreateEditPatientActivity", "Is admin: $isAdmin")

            if (isAdmin) {
                // Admin: mostrar campo cuidador
                binding.tilCaregiver.visibility = android.view.View.VISIBLE
            } else {
                // Terapeuta u otros: ocultar campo cuidador
                binding.tilCaregiver.visibility = android.view.View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.e("CreateEditPatientActivity", "Error in setupCaregiverVisibility", e)
            // En caso de error, ocultar el campo por seguridad
            binding.tilCaregiver.visibility = android.view.View.GONE
        }
    }

    private fun setupBMICalculation() {
        val heightWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateAndDisplayBMI()
            }
        }

        val weightWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateAndDisplayBMI()
            }
        }

        binding.etHeight.addTextChangedListener(heightWatcher)
        binding.etWeight.addTextChangedListener(weightWatcher)
    }

    private fun calculateAndDisplayBMI() {
        val heightText = binding.etHeight.text.toString()
        val weightText = binding.etWeight.text.toString()

        if (heightText.isNotBlank() && weightText.isNotBlank()) {
            try {
                val height = heightText.toFloat()
                val weight = weightText.toFloat()

                if (height > 0 && weight > 0) {
                    val heightInMeters = height / 100
                    val bmi = weight / (heightInMeters * heightInMeters)
                    val roundedBMI = kotlin.math.round(bmi * 100) / 100

                    binding.tvBMIValue.text = String.format("%.1f", roundedBMI)
                    setupBMICategory(roundedBMI)
                    binding.cvBMICard.visibility = android.view.View.VISIBLE
                } else {
                    hideBMICard()
                }
            } catch (e: NumberFormatException) {
                hideBMICard()
            }
        } else {
            hideBMICard()
        }
    }

    private fun setupBMICategory(bmi: Float) {
        val (category, colorRes) = when {
            bmi < 18.5f -> "Bajo peso" to R.color.warning
            bmi < 25f -> "Normal" to R.color.success
            bmi < 30f -> "Sobrepeso" to R.color.warning
            else -> "Obesidad" to R.color.error
        }

        binding.tvBMICategory.text = category
        binding.tvBMICategory.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, colorRes)
    }

    private fun hideBMICard() {
        binding.cvBMICard.visibility = android.view.View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}