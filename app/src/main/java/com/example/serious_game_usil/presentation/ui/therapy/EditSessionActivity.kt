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
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.`interface`.UpdateSessionRequest
import com.example.serious_game_usil.`interface`.PatientListItem
import com.example.serious_game_usil.network.RetrofitClient
import com.seriousgame.app.navigation.RouteNavigator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditSessionActivity : AppCompatActivity() {

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
    private lateinit var spinnerEstado: MaterialAutoCompleteTextView

    private var selectedDate = Calendar.getInstance()
    private var patients = listOf<PatientListItem>()
    private var therapists = listOf<UserListItem>()
    private var selectedPatientId: Int? = null
    private var selectedTherapistId: Int? = null
    private var sessionId: Int = -1
    private var currentSession: TherapySession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación
        AuthManager.init(this)
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        // Configurar token
        AuthManager.getAccessToken()?.let { token ->
            RetrofitClient.setAuthToken(token)
        } ?: run {
            RouteNavigator.navigateToLogin(this)
            return
        }

        sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (sessionId == -1) {
            Toast.makeText(this, "Error: ID de sesión no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_edit_session)
        setupViews()
        setupViewModel()
        loadInitialData()
    }

    private fun setupViews() {
        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Sesión"
        toolbar.setNavigationOnClickListener { finish() }

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

        // Add estado spinner if layout supports it
        try {
            spinnerEstado = findViewById(R.id.spinnerEstado)
            setupEstadoSpinner()
        } catch (e: Exception) {
            // Estado spinner not in layout
        }

        // Setup date/time pickers
        setupDateTimePickers()

        // Setup dropdowns
        setupTipoSesionSpinner()
        setupModalidadSpinner()

        // Save button
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        btnSave.text = "Actualizar Sesión"
        btnSave.setOnClickListener { updateSession() }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TherapySessionViewModel::class.java]

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@EditSessionActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.successMessage.collect { message ->
                message?.let {
                    Toast.makeText(this@EditSessionActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }

        // Load current session
        lifecycleScope.launch {
            viewModel.currentSession.collect { session ->
                session?.let {
                    currentSession = it
                    populateFields(it)
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModel.loadSession(sessionId)
        viewModel.loadPatients()
        loadTherapists()

        lifecycleScope.launch {
            viewModel.patients.collect { patientsList ->
                patients = patientsList
                setupPatientsSpinner()
            }
        }

        lifecycleScope.launch {
            viewModel.availableTherapists.collect { therapistsList ->
                therapists = therapistsList
                setupTherapistsSpinner()
            }
        }
    }

    private fun populateFields(session: TherapySession) {
        // Set selected IDs
        selectedPatientId = session.paciente.id
        selectedTherapistId = session.terapeuta.id

        // Populate fields
        etFechaSesion.setText(session.fechaSesion)
        etHoraInicio.setText(session.horaInicio)
        etHoraFin.setText(session.horaFin)
        etDuracion.setText(session.duracion?.toString() ?: "")
        etUbicacion.setText(session.ubicacion)
        etDireccion.setText(session.direccion)
        etDescripcion.setText(session.descripcion)
        etObjetivos.setText(session.objetivos?.joinToString("\n") ?: "")
        etMateriales.setText(session.materiales?.joinToString("\n") ?: "")

        // Set spinners
        spinnerTipoSesion.setText(session.tipoSesion, false)
        spinnerModalidad.setText(session.modalidad, false)

        // Set estado if available
        try {
            spinnerEstado.setText(TherapySessionViewModel.getStatusDisplayName(session.estado ?: ""), false)
        } catch (e: Exception) {
            // Estado spinner not available
        }

        // Parse and set date
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate.time = dateFormat.parse(session.fechaSesion) ?: Date()
        } catch (e: Exception) {
            // Keep current date
        }
    }

    private fun setupDateTimePickers() {
        etFechaSesion.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    updateDateDisplay()
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        etHoraInicio.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    etHoraInicio.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                    calculateDuration()
                },
                9, 0, true
            ).show()
        }

        etHoraFin.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    etHoraFin.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                    calculateDuration()
                },
                10, 0, true
            ).show()
        }
    }

    private fun setupPatientsSpinner() {
        val patientNames = patients.map { "${it.nombresApellidos} (${it.edad} años)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, patientNames)
        spinnerPatient.setAdapter(adapter)

        // Set current patient if found
        currentSession?.let { session ->
            val currentPatientIndex = patients.indexOfFirst { it.id == session.paciente.id }
            if (currentPatientIndex >= 0) {
                spinnerPatient.setText(patientNames[currentPatientIndex], false)
            }
        }

        spinnerPatient.setOnItemClickListener { _, _, position, _ ->
            selectedPatientId = patients[position].id
        }
    }

    private fun setupTherapistsSpinner() {
        val therapistNames = therapists.map { it.nombresApellidos }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, therapistNames)
        spinnerTerapeuta.setAdapter(adapter)

        // Set current therapist if found
        currentSession?.let { session ->
            val currentTherapistIndex = therapists.indexOfFirst { it.id == session.terapeuta.id }
            if (currentTherapistIndex >= 0) {
                spinnerTerapeuta.setText(therapistNames[currentTherapistIndex], false)
            }
        }

        spinnerTerapeuta.setOnItemClickListener { _, _, position, _ ->
            selectedTherapistId = therapists[position].id
        }
    }

    private fun setupTipoSesionSpinner() {
        val tipos = arrayOf("Presencial", "Virtual", "Domiciliaria", "Evaluación", "Seguimiento")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tipos)
        spinnerTipoSesion.setAdapter(adapter)
    }

    private fun setupModalidadSpinner() {
        val modalidades = arrayOf("Individual", "Grupal", "Familiar")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modalidades)
        spinnerModalidad.setAdapter(adapter)
    }

    private fun setupEstadoSpinner() {
        val estados = arrayOf("programada", "confirmada", "en_curso", "completada", "cancelada", "reprogramada")
        val estadosDisplay = estados.map { TherapySessionViewModel.getStatusDisplayName(it) }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosDisplay)
        spinnerEstado.setAdapter(adapter)
    }

    private fun updateDateDisplay() {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etFechaSesion.setText(format.format(selectedDate.time))
    }

    private fun calculateDuration() {
        val inicio = etHoraInicio.text.toString()
        val fin = etHoraFin.text.toString()

        if (inicio.isNotEmpty() && fin.isNotEmpty()) {
            try {
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startTime = format.parse(inicio)
                val endTime = format.parse(fin)

                if (startTime != null && endTime != null) {
                    val diffMs = endTime.time - startTime.time
                    val diffMinutes = diffMs / (1000 * 60)
                    etDuracion.setText(diffMinutes.toString())
                }
            } catch (e: Exception) {
                // Error parsing times
            }
        }
    }

    private fun updateSession() {
        if (!validateForm()) return

        val objetivos = etObjetivos.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val materiales = etMateriales.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val updateRequest = UpdateSessionRequest(
            fecha_sesion = etFechaSesion.text.toString(),
            hora_inicio = etHoraInicio.text.toString(),
            hora_fin = etHoraFin.text.toString(),
            ubicacion = etUbicacion.text.toString(),
            direccion = etDireccion.text.toString(),
            descripcion = etDescripcion.text.toString(),
            objetivos = objetivos.ifEmpty { null },
            materiales = materiales.ifEmpty { null },
            notas_terapeuta = null,
            estado = try {
                val displayName = spinnerEstado.text.toString()
                val estados = arrayOf("programada", "confirmada", "en_curso", "completada", "cancelada", "reprogramada")
                val estadosDisplay = estados.map { TherapySessionViewModel.getStatusDisplayName(it) }
                val index = estadosDisplay.indexOf(displayName)
                if (index >= 0) estados[index] else null
            } catch (e: Exception) { null },
            tipo_sesion = spinnerTipoSesion.text.toString().ifEmpty { null },
            modalidad = spinnerModalidad.text.toString().ifEmpty { null }
        )

        viewModel.updateSession(sessionId, updateRequest)
    }

    private fun validateForm(): Boolean {
        if (etFechaSesion.text.isNullOrEmpty()) {
            etFechaSesion.error = "Selecciona una fecha"
            return false
        }

        if (etHoraInicio.text.isNullOrEmpty()) {
            etHoraInicio.error = "Selecciona hora de inicio"
            return false
        }

        if (etHoraFin.text.isNullOrEmpty()) {
            etHoraFin.error = "Selecciona hora de fin"
            return false
        }

        return true
    }

    private fun loadTherapists() {
        // Verificar el rol del usuario actual
        val userRoles = AuthManager.getUserRoles()
        val isAdmin = userRoles.any { it.lowercase() in listOf("admin", "administrador") }
        val isTherapist = userRoles.any { it.lowercase() in listOf("terapeuta", "therapist", "tr") }

        Log.d("EditSession", "User roles: $userRoles, isAdmin: $isAdmin, isTherapist: $isTherapist")

        if (isAdmin) {
            // Los administradores pueden ver todos los terapeutas disponibles
            viewModel.loadAvailableTherapists()
        } else if (isTherapist) {
            // Los terapeutas solo pueden asignarse a sí mismos
            val currentUserId = AuthManager.getUserId()
            val currentUserName = AuthManager.getNombresApellidos().ifEmpty { "Usuario Actual" }
            val currentUserEmail = AuthManager.getCorreo().ifEmpty { "email@example.com" }

            if (currentUserId > 0) {
                val currentTherapist = UserListItem(
                    id = currentUserId,
                    nombresApellidos = currentUserName,
                    correo = currentUserEmail,
                    nombreUsuario = currentUserName,
                    telefono = AuthManager.getTelefono() ?: "",
                    activo = true,
                    tipoDocumento = AuthManager.getTipoDocumento() ?: "DNI",
                    numeroDocumento = AuthManager.getNumDocumento() ?: "",
                    sexo = AuthManager.getSexo() ?: "M",
                    fechaNacimiento = "1990-01-01",
                    fotoMovil = AuthManager.getFoto(),
                    roles = emptyList(), // Lista vacía de roles por ahora
                    createdAt = "",
                    updatedAt = ""
                )
                therapists = listOf(currentTherapist)
                setupTherapistsSpinner()

                Log.d("EditSession", "Therapist auto-assigned: $currentUserName (ID: $currentUserId)")
            } else {
                Log.w("EditSession", "User ID is invalid: $currentUserId")
                Toast.makeText(this, "Error: No se pudo obtener información del usuario", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Para otros roles, mostrar error
            Log.w("EditSession", "User without proper roles trying to edit session. Roles: $userRoles")
            Toast.makeText(this, "No tienes permisos para editar sesiones", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"

        fun newIntent(context: Context, sessionId: Int): Intent {
            return Intent(context, EditSessionActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
    }
}