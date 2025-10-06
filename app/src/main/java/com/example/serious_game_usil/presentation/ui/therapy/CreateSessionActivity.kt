package com.example.serious_game_usil.presentation.ui.therapy

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.CreateTherapySessionRequest
import com.example.serious_game_usil.`interface`.PatientListItem
import com.example.serious_game_usil.network.RetrofitClient
import com.google.android.material.appbar.MaterialToolbar
import com.seriousgame.app.navigation.RouteNavigator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CreateSessionActivity : AppCompatActivity() {

    private lateinit var viewModel: TherapySessionViewModel
    private lateinit var progressBar: ProgressBar

    // Form fields
    private lateinit var spinnerPatient: MaterialAutoCompleteTextView
    private lateinit var spinnerTerapeuta: MaterialAutoCompleteTextView
    private lateinit var etFechaSesion: TextInputEditText
    private lateinit var etHoraInicio: TextInputEditText
    private lateinit var etHoraFin: TextInputEditText
    private lateinit var etDuracion: TextInputEditText
    private lateinit var spinnerTipoSesion: MaterialAutoCompleteTextView
    private lateinit var spinnerModalidad: MaterialAutoCompleteTextView
    private lateinit var etUbicacion: TextInputEditText
    private lateinit var etDireccion: TextInputEditText
    private lateinit var etDescripcion: TextInputEditText
    private lateinit var etObjetivos: TextInputEditText
    private lateinit var etMateriales: TextInputEditText

    private var selectedDate = Calendar.getInstance()
    private var patients = listOf<PatientListItem>()
    private var therapists = listOf<UserListItem>()
    private var selectedPatientId: Int? = null
    private var selectedTherapistId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar AuthManager
        AuthManager.init(this)

        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        // Configurar el token del usuario en RetrofitClient
        AuthManager.getAccessToken()?.let { token ->
            RetrofitClient.setAuthToken(token)
            Log.d("CreateSessionActivity", "Token configurado: ${token.take(20)}...")
        } ?: run {
            Log.e("CreateSessionActivity", "No se encontró token de usuario")
            RouteNavigator.navigateToLogin(this)
            return
        }

        setContentView(R.layout.activity_create_session)
        setupViewModel()
        initViews()
        setupObservers()
        setupSpinners()
        loadPatients()
        loadTherapists()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TherapySessionViewModel::class.java]
    }

    private fun initViews() {
        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)

        // Form fields
        spinnerPatient = findViewById(R.id.spinnerPatient)
        spinnerTerapeuta = findViewById(R.id.spinnerTerapeuta)
        etFechaSesion = findViewById(R.id.etFechaSesion)
        etHoraInicio = findViewById(R.id.etHoraInicio)
        etHoraFin = findViewById(R.id.etHoraFin)
        etDuracion = findViewById(R.id.etDuracion)
        spinnerTipoSesion = findViewById(R.id.spinnerTipoSesion)
        spinnerModalidad = findViewById(R.id.spinnerModalidad)
        etUbicacion = findViewById(R.id.etUbicacion)
        etDireccion = findViewById(R.id.etDireccion)
        etDescripcion = findViewById(R.id.etDescripcion)
        etObjetivos = findViewById(R.id.etObjetivos)
        etMateriales = findViewById(R.id.etMateriales)

        // Date picker
        etFechaSesion.setOnClickListener { showDatePicker() }

        // Time pickers
        etHoraInicio.setOnClickListener { showTimePicker(true) }
        etHoraFin.setOnClickListener { showTimePicker(false) }

        // Buttons
        findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { createSession() }

        // Patient selection
        spinnerPatient.setOnItemClickListener { _, _, position, _ ->
            selectedPatientId = patients[position].id
        }

        // Therapist selection
        spinnerTerapeuta.setOnItemClickListener { _, _, position, _ ->
            selectedTherapistId = therapists[position].id
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                findViewById<MaterialButton>(R.id.btnGuardar).isEnabled = !isLoading
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@CreateSessionActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.createResult.collect { success ->
                if (success) {
                    Toast.makeText(this@CreateSessionActivity, "Sesión creada exitosamente", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.patients.collect { patientsList ->
                patients = patientsList
                setupPatientSpinner()
            }
        }

        lifecycleScope.launch {
            viewModel.availableTherapists.collect { therapistsList ->
                Log.d("CreateSession", "Observer: Received therapists list size=${therapistsList.size}")
                therapists = therapistsList

                // Solo configurar si es admin y hay terapeutas
                val isAdmin = AuthManager.getUserRoles().any { it.lowercase() in listOf("admin", "administrador", "ad") }
                if (isAdmin) {
                    if (therapistsList.isNotEmpty()) {
                        Log.d("CreateSession", "Setting up therapist spinner with ${therapistsList.size} items")
                        setupTherapistSpinner()
                    } else {
                        Log.e("CreateSession", "ERROR: No therapists available in system!")
                        Toast.makeText(
                            this@CreateSessionActivity,
                            "ERROR: No hay terapeutas disponibles en el sistema",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupSpinners() {
        // Session types
        val sessionTypes = arrayOf("Terapia Individual", "Terapia Grupal", "Evaluación", "Seguimiento")
        val sessionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sessionTypes)
        spinnerTipoSesion.setAdapter(sessionAdapter)
        spinnerTipoSesion.setText("Terapia Individual", false)

        // Modalities
        val modalities = arrayOf("Presencial", "Virtual", "Domicilio")
        val modalityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modalities)
        spinnerModalidad.setAdapter(modalityAdapter)
        spinnerModalidad.setText("Presencial", false)
    }

    private fun setupPatientSpinner() {
        val patientNames = patients.map { "${it.nombresApellidos} (${it.serialId})" }
        val patientAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, patientNames)
        spinnerPatient.setAdapter(patientAdapter)
    }

    private fun setupTherapistSpinner() {
        val therapistNames = therapists.map { "${it.nombresApellidos} - ${it.correo}" }
        val therapistAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, therapistNames)
        spinnerTerapeuta.setAdapter(therapistAdapter)
    }

    private fun loadPatients() {
        viewModel.loadPatients()
    }

    private fun loadTherapists() {
        // Verificar el rol del usuario actual
        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador", "ad") }
        val isTherapist = userRoles.any { it.lowercase() in listOf("terapeuta", "therapist", "tr") }

        Log.d("CreateSession", "User roles: $userRoles, isAdmin: $isAdmin, isTherapist: $isTherapist")

        if (isAdmin) {
            // ADMIN: Mostrar campo de terapeuta y cargar TODOS los terapeutas
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutTerapeuta).visibility = View.VISIBLE
            Log.d("CreateSession", "Admin: Loading ALL therapists...")
            viewModel.loadAvailableTherapists()
            // El observer está en setupObservers() línea 166-176
        } else if (isTherapist) {
            // TERAPEUTA: OCULTAR campo y auto-asignar
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutTerapeuta).visibility = View.GONE

            val currentUserId = AuthManager.getUserId()
            val currentUserName = AuthManager.getNombresApellidos().ifEmpty { "Usuario Actual" }

            Log.d("CreateSession", "Therapist: Auto-assigning UserID: $currentUserId")

            if (currentUserId > 0) {
                // Auto-asignar el terapeuta actual SIN mostrar el campo
                selectedTherapistId = currentUserId
                Log.d("CreateSession", "Therapist auto-assigned silently: $currentUserName (ID: $currentUserId)")
            } else {
                Log.e("CreateSession", "User ID is invalid: $currentUserId")
                Toast.makeText(this, "Error: No se pudo obtener información del usuario", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            // Otros roles no pueden crear sesiones
            Log.e("CreateSession", "User without proper roles. Roles: $userRoles")
            Toast.makeText(this, "No tienes permisos para crear sesiones", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                etFechaSesion.setText(dateFormat.format(selectedDate.time))
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )

        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeString = timeFormat.format(calendar.time)

                if (isStartTime) {
                    etHoraInicio.setText(timeString)
                    // Auto-calculate end time (add 1 hour)
                    calendar.add(Calendar.HOUR_OF_DAY, 1)
                    etHoraFin.setText(timeFormat.format(calendar.time))
                    etDuracion.setText("60")
                } else {
                    etHoraFin.setText(timeString)
                    calculateDuration()
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun calculateDuration() {
        val startTime = etHoraInicio.text.toString()
        val endTime = etHoraFin.text.toString()

        if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            try {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val start = timeFormat.parse(startTime)
                val end = timeFormat.parse(endTime)

                if (start != null && end != null) {
                    var durationMillis = end.time - start.time

                    // Si la hora fin es menor que la de inicio, asumir que es del día siguiente
                    if (durationMillis < 0) {
                        durationMillis += 24 * 60 * 60 * 1000
                    }

                    val durationMinutes = durationMillis / (1000 * 60)

                    // Backend solo acepta entre 30 y 45 minutos
                    val finalDuration = when {
                        durationMinutes < 30 -> 30
                        durationMinutes > 45 -> 45
                        else -> durationMinutes
                    }

                    etDuracion.setText(finalDuration.toString())
                    Log.d("CreateSessionActivity", "Duration calculated: $finalDuration minutes (from $startTime to $endTime)")
                }
            } catch (e: Exception) {
                Log.e("CreateSessionActivity", "Error calculating duration", e)
                etDuracion.setText("30") // Default 30 minutos
            }
        }
    }

    private fun createSession() {
        if (!validateForm()) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sessionDate = dateFormat.format(selectedDate.time)

        // Asegurar formato de hora HH:mm
        val startTime = etHoraInicio.text.toString().let { time ->
            if (time.matches(Regex("\\d{2}:\\d{2}"))) time else "09:00"
        }
        val endTime = etHoraFin.text.toString().let { time ->
            if (time.matches(Regex("\\d{2}:\\d{2}"))) time else "10:00"
        }

        val objectives = etObjetivos.text.toString().split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val materials = etMateriales.text.toString().split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        Log.d("CreateSessionActivity", """
            Preparing to create session:
            - Patient ID: $selectedPatientId
            - Date: $sessionDate
            - Start Time: $startTime
            - End Time: $endTime
            - Session Type: ${spinnerTipoSesion.text}
            - Modality: ${spinnerModalidad.text}
        """.trimIndent())

        val createRequest = CreateTherapySessionRequest(
            pacienteId = selectedPatientId!!,
            terapeutaId = selectedTherapistId,
            fechaSesion = sessionDate,
            horaInicio = startTime,
            horaFin = endTime,
            duracion = etDuracion.text.toString().toIntOrNull() ?: 60,
            ubicacion = etUbicacion.text.toString().takeIf { it.isNotEmpty() },
            direccion = etDireccion.text.toString().takeIf { it.isNotEmpty() },
            descripcion = etDescripcion.text.toString().takeIf { it.isNotEmpty() },
            objetivos = objectives.takeIf { it.isNotEmpty() },
            materiales = materials.takeIf { it.isNotEmpty() },
            tipoSesion = spinnerTipoSesion.text.toString().takeIf { it.isNotEmpty() },
            modalidad = spinnerModalidad.text.toString().takeIf { it.isNotEmpty() }
        )

        viewModel.createSession(createRequest)
    }

    private fun validateForm(): Boolean {
        if (selectedPatientId == null) {
            Toast.makeText(this, "Selecciona un paciente", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedTherapistId == null) {
            Toast.makeText(this, "Selecciona un terapeuta", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etFechaSesion.text.toString().isEmpty()) {
            Toast.makeText(this, "Selecciona una fecha", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etHoraInicio.text.toString().isEmpty()) {
            Toast.makeText(this, "Selecciona hora de inicio", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etHoraFin.text.toString().isEmpty()) {
            Toast.makeText(this, "Selecciona hora de fin", Toast.LENGTH_SHORT).show()
            return false
        }

        val duration = etDuracion.text.toString().toIntOrNull()
        if (duration == null || duration <= 0) {
            Toast.makeText(this, "Ingresa una duración válida", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, CreateSessionActivity::class.java)
        }
    }
}