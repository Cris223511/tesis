package com.example.serious_game_usil.presentation.ui.caregivers

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.CreateCaregiverRequest
import com.example.serious_game_usil.data.UpdateCaregiverRequest
import com.example.serious_game_usil.databinding.ActivityCreateEditCaregiverBinding
import com.example.serious_game_usil.repository.CaregiverRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CreateEditCaregiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEditCaregiverBinding
    private lateinit var caregiverRepository: CaregiverRepository

    private var caregiverId: Int = -1
    private var isEditMode: Boolean = false
    private var selectedImageBase64: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleImageSelection(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // La imagen se guarda en imageUri que definiremos
            // handleImageSelection(imageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEditCaregiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        caregiverRepository = CaregiverRepository.getInstance()

        // Check if editing existing caregiver
        caregiverId = intent.getIntExtra("caregiver_id", -1)
        isEditMode = caregiverId != -1

        setupViews()
        setupSpinners()
        setupClickListeners()

        if (isEditMode) {
            loadCaregiverData()
        }
    }

    private fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEditMode) "Editar Cuidador" else "Crear Cuidador"

        // Update button text and password info visibility
        binding.btnSave.text = if (isEditMode) "Actualizar Cuidador" else "Guardar Cuidador"
        binding.tvPasswordInfo.visibility = if (isEditMode) android.view.View.GONE else android.view.View.VISIBLE

        // Setup roles - only cuidador should be available and checked
        binding.cbCuidador.isChecked = true
        binding.cbCuidador.isEnabled = false // No se puede desmarcar
        binding.cbAdministrador.isEnabled = false
        binding.cbUsuarios.isEnabled = false
    }

    private fun setupSpinners() {
        // Document type spinner
        val documentTypes = arrayOf("DNI", "Pasaporte", "Carnet de Extranjería")
        val documentAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, documentTypes)
        binding.actvTipoDocumento.setAdapter(documentAdapter)
        binding.actvTipoDocumento.setText(documentTypes[0], false) // Default to DNI

        // Gender spinner
        val genders = arrayOf("Masculino", "Femenino")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.actvGenero.setAdapter(genderAdapter)
    }

    private fun setupClickListeners() {
        // Date picker for birth date
        binding.etFechaNacimiento.setOnClickListener {
            showDatePicker()
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            finish()
        }

        // Save button
        binding.btnSave.setOnClickListener {
            if (validateForm()) {
                if (isEditMode) {
                    updateCaregiver()
                } else {
                    createCaregiver()
                }
            }
        }

        // Photo selector button
        binding.btnSelectPhoto.setOnClickListener {
            showImagePickerOptions()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.etFechaNacimiento.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR) - 25, // Default to 25 years ago
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis() // No future dates
        datePickerDialog.show()
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validate required fields
        if (binding.etNombresApellidos.text.isNullOrBlank()) {
            binding.tilNombresApellidos.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilNombresApellidos.error = null
        }

        if (binding.etNumeroDocumento.text.isNullOrBlank()) {
            binding.tilNumeroDocumento.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilNumeroDocumento.error = null
        }

        if (binding.etCorreo.text.isNullOrBlank()) {
            binding.tilCorreo.error = "Campo requerido"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(binding.etCorreo.text.toString()).matches()) {
            binding.tilCorreo.error = "Correo electrónico inválido"
            isValid = false
        } else {
            binding.tilCorreo.error = null
        }

        if (binding.actvTipoDocumento.text.isNullOrBlank()) {
            binding.tilTipoDocumento.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilTipoDocumento.error = null
        }

        if (binding.actvGenero.text.isNullOrBlank()) {
            binding.tilGenero.error = "Campo requerido"
            isValid = false
        } else {
            binding.tilGenero.error = null
        }

        return isValid
    }

    private fun createCaregiver() {
        val request = CreateCaregiverRequest(
            nombresApellidos = binding.etNombresApellidos.text.toString().trim(),
            tipoDocumento = binding.actvTipoDocumento.text.toString(),
            numDocumento = binding.etNumeroDocumento.text.toString().trim(),
            sexo = binding.actvGenero.text.toString(),
            telefono = binding.etTelefono.text.toString().trim().ifBlank { null },
            correo = binding.etCorreo.text.toString().trim(),
            fotoMovil = selectedImageBase64,
            password = generateTemporaryPassword()
        )

        lifecycleScope.launch {
            caregiverRepository.createCaregiver(request).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Cuidador creado exitosamente", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error al crear cuidador: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateCaregiver() {
        val request = UpdateCaregiverRequest(
            nombresApellidos = binding.etNombresApellidos.text.toString().trim(),
            correo = binding.etCorreo.text.toString().trim(),
            telefono = binding.etTelefono.text.toString().trim().ifBlank { null },
            tipoDocumento = binding.actvTipoDocumento.text.toString(),
            numDocumento = binding.etNumeroDocumento.text.toString().trim(),
            sexo = binding.actvGenero.text.toString(),
            fotoMovil = selectedImageBase64,
            activo = true
        )

        lifecycleScope.launch {
            caregiverRepository.updateCaregiver(caregiverId, request).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Cuidador actualizado exitosamente", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error al actualizar cuidador: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadCaregiverData() {
        lifecycleScope.launch {
            caregiverRepository.getCaregiverDetail(caregiverId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val caregiver = result.data.caregiver
                        populateForm(caregiver)
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error al cargar datos del cuidador", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CreateEditCaregiverActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun populateForm(caregiver: com.example.serious_game_usil.data.CaregiverDetail) {
        binding.etNombresApellidos.setText(caregiver.nombresApellidos)
        binding.actvTipoDocumento.setText(caregiver.tipoDocumento, false)
        binding.etNumeroDocumento.setText(caregiver.numDocumento)
        binding.actvGenero.setText(caregiver.sexo, false)
        binding.etTelefono.setText(caregiver.telefono)
        binding.etCorreo.setText(caregiver.correo)
    }

    private fun showImagePickerOptions() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Seleccionar foto")
            .setMessage("¿Cómo quieres seleccionar la foto?")
            .setPositiveButton("Galería") { _, _ ->
                imagePickerLauncher.launch("image/*")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Comprimir y redimensionar la imagen
            val resizedBitmap = resizeBitmap(bitmap, 500, 500)

            // Convertir a Base64
            selectedImageBase64 = bitmapToBase64(resizedBitmap)

            // Mostrar preview
            binding.ivCaregiverPhoto.setImageBitmap(resizedBitmap)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        val maxRatio = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight

        if (maxRatio > bitmapRatio) {
            finalWidth = (maxHeight * bitmapRatio).toInt()
        } else {
            finalHeight = (maxWidth / bitmapRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun generateTemporaryPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8)
            .map { chars.random() }
            .joinToString("")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}