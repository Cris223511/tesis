package com.example.serious_game_usil.presentation.ui.therapy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.adapters.TherapySessionAdapter
import com.example.serious_game_usil.presentation.ui.padres.SessionDetailActivity
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SimpleTherapySessionsActivity : AppCompatActivity() {

    private lateinit var viewModel: TherapySessionViewModel
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var adapter: TherapySessionAdapter

    // Para filtro por paciente específico
    private var specificPatientId: Int? = null
    private var specificPatientName: String? = null

    private val createSessionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Recargar sesiones después de crear una nueva
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtener parámetros del intent (si se está filtrando por paciente específico)
        specificPatientId = intent.getIntExtra("patient_id", -1).takeIf { it != -1 }
        specificPatientName = intent.getStringExtra("patient_name")

        // Inicializar AuthManager
        AuthManager.init(this)

        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        // Configurar el token del usuario en RetrofitClient
        AuthManager.getAccessToken()?.let { token ->
            com.example.serious_game_usil.network.RetrofitClient.setAuthToken(token)
            android.util.Log.d("SimpleTherapySessionsActivity", "Token configurado: ${token.take(20)}...")
        } ?: run {
            android.util.Log.e("SimpleTherapySessionsActivity", "No se encontró token de usuario")
            RouteNavigator.navigateToLogin(this)
            return
        }

        createLayout()
        setupViewModel()
        loadData()
    }

    private fun createLayout() {
        setContentView(R.layout.activity_therapy_sessions)

        // Inicializar vistas del layout
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerViewSessions)
        emptyStateLayout = findViewById(R.id.layoutEmptyState)

        // Configurar RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        setupAdapter()

        // Configurar toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Actualizar título si se está filtrando por paciente específico
        if (specificPatientName != null) {
            toolbar.title = "Sesiones de $specificPatientName"
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Configurar filtros
        setupFilters()

        // Configurar búsqueda
        setupSearch()

        // Configurar FAB
        val fabAddSession = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddSession)
        fabAddSession.setOnClickListener {
            val intent = CreateSessionActivity.newIntent(this)
            createSessionLauncher.launch(intent)
        }
    }

    private fun setupFilters() {
        val btnFilterAll = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFilterAll)
        val btnFilterProgrammed = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFilterProgrammed)
        val btnFilterCompleted = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFilterCompleted)

        // Configurar estado inicial (Todas seleccionado)
        selectFilterButton(btnFilterAll, btnFilterProgrammed, btnFilterCompleted)

        btnFilterAll.setOnClickListener {
            selectFilterButton(btnFilterAll, btnFilterProgrammed, btnFilterCompleted)
            viewModel.filterSessions(null)
        }

        btnFilterProgrammed.setOnClickListener {
            selectFilterButton(btnFilterProgrammed, btnFilterAll, btnFilterCompleted)
            viewModel.filterSessions("programada")
        }

        btnFilterCompleted.setOnClickListener {
            selectFilterButton(btnFilterCompleted, btnFilterAll, btnFilterProgrammed)
            viewModel.filterSessions("completada")
        }
    }

    private fun selectFilterButton(selected: com.google.android.material.button.MaterialButton, vararg others: com.google.android.material.button.MaterialButton) {
        // Botón seleccionado
        selected.setTextColor(getColor(android.R.color.white))
        selected.setBackgroundColor(getColor(R.color.primary))

        // Botones no seleccionados
        others.forEach { button ->
            button.setTextColor(getColor(R.color.primary))
            button.setBackgroundColor(getColor(android.R.color.white))
        }
    }

    private fun setupSearch() {
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Buscar en tiempo real después de 2 caracteres
                if (newText != null) {
                    if (newText.length >= 2) {
                        performSearch(newText)
                    } else if (newText.isEmpty()) {
                        performSearch(null) // Mostrar todas las sesiones
                    }
                }
                return true
            }
        })

        // Configurar el botón de limpiar búsqueda
        searchView.setOnCloseListener {
            performSearch(null)
            false
        }
    }

    private fun performSearch(query: String?) {
        viewModel.searchSessions(query)
    }

    private fun setupAdapter() {
        adapter = TherapySessionAdapter(
            sessions = emptyList(),
            onEditClick = { session ->
                val intent = EditSessionActivity.newIntent(this, session.id)
                startActivity(intent)
            },
            onViewClick = { session ->
                val intent = SessionDetailActivity.newIntent(this, session.id)
                startActivity(intent)
            },
            onDeleteClick = { session ->
                showDeleteConfirmationDialog(session)
            }
        )
        recyclerView.adapter = adapter
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
                    Toast.makeText(this@SimpleTherapySessionsActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                    showError(it)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sessions.collect { sessions ->
                displaySessions(sessions)
            }
        }
    }

    private fun loadData() {
        viewModel.loadAllSessions()
    }

    private fun displaySessions(sessions: List<com.example.serious_game_usil.`interface`.TherapySession>) {
        // Filtrar por paciente específico si está disponible
        val filteredSessions = if (specificPatientId != null) {
            sessions.filter { session ->
                // Filtrar por nombre del paciente usando el objeto anidado
                specificPatientName?.let { patientName ->
                    session.paciente.nombresApellidos.equals(patientName, ignoreCase = true)
                } ?: false
            }
        } else {
            sessions
        }

        if (filteredSessions.isEmpty()) {
            // Mostrar estado vacío
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            updateEmptyStateMessage()
        } else {
            // Mostrar RecyclerView con datos
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE

            // Actualizar adapter con las sesiones filtradas
            adapter.updateSessions(filteredSessions)
        }
    }

    private fun updateEmptyStateMessage() {
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        val currentQuery = searchView.query?.toString()

        val emptyTitle = findViewById<TextView>(R.id.tvEmptyTitle)
        val emptySubtitle = findViewById<TextView>(R.id.tvEmptySubtitle)

        when {
            !currentQuery.isNullOrBlank() -> {
                emptyTitle.text = "Sin resultados de búsqueda"
                emptySubtitle.text = "No se encontraron sesiones que coincidan con \"$currentQuery\""
            }
            specificPatientName != null -> {
                emptyTitle.text = "Sin sesiones para $specificPatientName"
                emptySubtitle.text = "Este paciente no tiene sesiones registradas"
            }
            viewModel.currentFilter != null -> {
                val filterName = when(viewModel.currentFilter) {
                    "programada" -> "programadas"
                    "completada" -> "completadas"
                    else -> viewModel.currentFilter
                }
                emptyTitle.text = "Sin sesiones $filterName"
                emptySubtitle.text = "No hay sesiones $filterName para mostrar"
            }
            else -> {
                emptyTitle.text = "No se encontraron sesiones"
                emptySubtitle.text = "Crea tu primera sesión terapéutica"
            }
        }
    }


    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun showError(error: String) {
        // Ocultar RecyclerView y mostrar estado vacío
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE

        // Mostrar error como Toast
        Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_LONG).show()
    }

    private fun showDeleteConfirmationDialog(session: com.example.serious_game_usil.`interface`.TherapySession) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar sesión?")
            .setMessage("¿Estás seguro de que deseas eliminar la sesión de ${session.paciente.nombresApellidos}? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteSession(session: com.example.serious_game_usil.`interface`.TherapySession) {
        lifecycleScope.launch {
            try {
                // TODO: Implementar eliminación de sesión en el ViewModel
                viewModel.deleteSession(session.id)
                Toast.makeText(this@SimpleTherapySessionsActivity, "Sesión eliminada exitosamente", Toast.LENGTH_SHORT).show()
                loadData() // Recargar la lista
            } catch (e: Exception) {
                Toast.makeText(this@SimpleTherapySessionsActivity, "Error al eliminar sesión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
        } else {
            loadData()
        }
    }

    companion object {
        fun newIntent(context: android.content.Context): android.content.Intent {
            return android.content.Intent(context, SimpleTherapySessionsActivity::class.java)
        }
    }
}