package com.example.serious_game_usil.presentation.ui.progress

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityProgressDetailBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.utils.ImageUtils
import com.google.android.material.tabs.TabLayoutMediator

class ProgressDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressDetailBinding
    private lateinit var progressViewModel: ProgressViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProgressDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViewModel()
        setupViewPager()
        loadProgressData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Progreso Terapéutico"
    }

    private fun setupViewModel() {
        progressViewModel = ViewModelProvider(this)[ProgressViewModel::class.java]

        progressViewModel.allChildrenProgress.observe(this) { progressList ->
            progressList?.let {
                updateUI(it)
            }
        }

        progressViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        progressViewModel.error.observe(this) { error ->
            error?.let {
                binding.errorText.text = it
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun setupViewPager() {
        val adapter = ProgressPagerAdapter()
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Evolución"
                1 -> "Pacientes"
                2 -> "Indicadores"
                else -> "Tab $position"
            }
        }.attach()
    }

    private fun loadProgressData() {
        progressViewModel.loadAllChildrenProgress()
    }

    private fun updateUI(progressList: List<com.example.serious_game_usil.data.ThreeMonthComparison>) {
        binding.errorText.visibility = View.GONE

        if (progressList.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = "No hay datos de progreso disponibles.\nComienza registrando sesiones terapéuticas."
        } else {
            binding.emptyStateText.visibility = View.GONE
        }
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

    inner class ProgressPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ProgressSummaryFragment()
                1 -> ProgressChildrenListFragment()
                2 -> ProgressStatsFragment()
                else -> ProgressSummaryFragment()
            }
        }
    }
}

class ProgressSummaryFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ThreeMonthComparisonFragment().onCreateView(inflater, container, savedInstanceState)
    }
}

class ProgressChildrenListFragment : Fragment() {
    private var currentPatientPage = 1
    private var currentCaregiverPage = 1
    private val itemsPerPage = 5

    private lateinit var patientsAdapter: PatientsPagedAdapter
    private lateinit var caregiversAdapter: CaregiversPagedAdapter
    private lateinit var viewModel: ProgressViewModel

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        val view = inflater.inflate(R.layout.fragment_progress_children_list, container, false)

        viewModel = ViewModelProvider(requireActivity())[ProgressViewModel::class.java]

        setupPatientsList(view)
        setupCaregiversList(view)
        setupPaginationControls(view)
        setupRoleBasedVisibility(view)

        loadData()

        return view
    }

    private fun setupPatientsList(view: View) {
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewPatients)
        patientsAdapter = PatientsPagedAdapter()
        recyclerView.adapter = patientsAdapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
    }

    private fun setupCaregiversList(view: View) {
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewCaregivers)
        caregiversAdapter = CaregiversPagedAdapter()
        recyclerView.adapter = caregiversAdapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
    }

    private fun setupRoleBasedVisibility(view: View) {
        val userRole = AuthManager.getUserRole()
        val caregiverSection: View? = view.findViewById(R.id.caregiverSection)
        val caregiverDivider: View? = view.findViewById(R.id.caregiverDivider)

        val isAdmin = userRole.equals("administrador", ignoreCase = true) ||
            userRole.equals("admin", ignoreCase = true)

        if (isAdmin) {
            caregiverSection?.visibility = View.VISIBLE
            caregiverDivider?.visibility = View.VISIBLE
        } else {
            caregiverSection?.visibility = View.GONE
            caregiverDivider?.visibility = View.GONE
        }
    }

    private fun setupPaginationControls(view: View) {
        val btnPreviousPatients = view.findViewById<android.widget.Button>(R.id.btnPreviousPatients)
        val btnNextPatients = view.findViewById<android.widget.Button>(R.id.btnNextPatients)

        btnPreviousPatients.setOnClickListener {
            if (currentPatientPage > 1) {
                currentPatientPage--
                updatePatientsPage()
            }
        }

        btnNextPatients.setOnClickListener {
            currentPatientPage++
            updatePatientsPage()
        }

        val btnPreviousCaregivers = view.findViewById<android.widget.Button>(R.id.btnPreviousCaregivers)
        val btnNextCaregivers = view.findViewById<android.widget.Button>(R.id.btnNextCaregivers)

        btnPreviousCaregivers.setOnClickListener {
            if (currentCaregiverPage > 1) {
                currentCaregiverPage--
                updateCaregiversPage()
            }
        }

        btnNextCaregivers.setOnClickListener {
            currentCaregiverPage++
            updateCaregiversPage()
        }
    }

    private fun loadData() {
        viewModel.loadPatients()
        val userRole = AuthManager.getUserRole()?.trim()?.lowercase().orEmpty()
        val isAdmin = userRole == "administrador" || userRole == "admin"
        if (isAdmin) {
            viewModel.loadCaregivers()
        }

        viewModel.patients.observe(viewLifecycleOwner) { patients ->
            patientsAdapter.setPatients(patients)
            updatePatientsPage()
        }

        viewModel.caregivers.observe(viewLifecycleOwner) { caregivers ->
            caregiversAdapter.setCaregivers(caregivers)
            updateCaregiversPage()
        }
    }

    private fun updatePatientsPage() {
        val btnPrevious = view?.findViewById<android.widget.Button>(R.id.btnPreviousPatients)
        val btnNext = view?.findViewById<android.widget.Button>(R.id.btnNextPatients)
        val pageInfo = view?.findViewById<android.widget.TextView>(R.id.tvPatientsPageInfo)

        btnPrevious?.isEnabled = currentPatientPage > 1

        val totalPages = kotlin.math.ceil(patientsAdapter.getTotalCount() / itemsPerPage.toDouble()).toInt()
        pageInfo?.text = "Página $currentPatientPage de ${kotlin.math.max(totalPages, 1)}"
        btnNext?.isEnabled = currentPatientPage < totalPages

        patientsAdapter.updatePage(currentPatientPage, itemsPerPage)
    }

    private fun updateCaregiversPage() {
        val btnPrevious = view?.findViewById<android.widget.Button>(R.id.btnPreviousCaregivers)
        val btnNext = view?.findViewById<android.widget.Button>(R.id.btnNextCaregivers)
        val pageInfo = view?.findViewById<android.widget.TextView>(R.id.tvCaregiversPageInfo)

        btnPrevious?.isEnabled = currentCaregiverPage > 1

        val totalPages = kotlin.math.ceil(caregiversAdapter.getTotalCount() / itemsPerPage.toDouble()).toInt()
        pageInfo?.text = "Página $currentCaregiverPage de ${kotlin.math.max(totalPages, 1)}"
        btnNext?.isEnabled = currentCaregiverPage < totalPages

        caregiversAdapter.updatePage(currentCaregiverPage, itemsPerPage)
    }

    inner class PatientsPagedAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<PatientsViewHolder>() {
        private var allPatients: List<com.example.serious_game_usil.data.PatientListItem> = emptyList()
        private var displayedPatients: List<com.example.serious_game_usil.data.PatientListItem> = emptyList()

        fun setPatients(patients: List<com.example.serious_game_usil.data.PatientListItem>) {
            allPatients = patients
            updatePage(currentPatientPage, itemsPerPage)
        }

        fun updatePage(page: Int, itemsPerPage: Int) {
            val startIndex = (page - 1) * itemsPerPage
            val endIndex = kotlin.math.min(startIndex + itemsPerPage, allPatients.size)
            displayedPatients = if (startIndex < allPatients.size) {
                allPatients.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            notifyDataSetChanged()
        }

        fun getTotalCount(): Int = allPatients.size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PatientsViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_patient, parent, false)
            return PatientsViewHolder(view)
        }

        override fun onBindViewHolder(holder: PatientsViewHolder, position: Int) {
            val patient = displayedPatients[position]
            holder.bind(patient)
        }

        override fun getItemCount(): Int = displayedPatients.size
    }

    inner class PatientsViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val photoImageView: android.widget.ImageView = itemView.findViewById(R.id.ivPatientPhoto)
        private val nameTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientName)
        private val documentTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientDocument)
        private val serialIdTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientSerialId)
        private val ageTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientAge)
        private val genderTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientGender)
        private val therapistTextView: android.widget.TextView = itemView.findViewById(R.id.tvTherapistName)
        private val caregiverTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverName)
        private val statusTextView: android.widget.TextView = itemView.findViewById(R.id.tvPatientStatus)
        private val editButton: View = itemView.findViewById(R.id.btnEditPatient)
        private val exportButton: View = itemView.findViewById(R.id.btnExportPatient)
        private val deleteButton: View = itemView.findViewById(R.id.btnDeletePatient)
        private val rippleOverlay: View = itemView.findViewById(R.id.rippleOverlay)

        fun bind(patient: com.example.serious_game_usil.data.PatientListItem) {
            nameTextView.text = patient.nombresApellidos
            documentTextView.text = "${getDocumentTypeAbbreviation(patient.tipoDocumento)}: ${patient.numDocumento}"
            serialIdTextView.text = "Serial: ${patient.serialId}"
            ageTextView.text = "${patient.edad} años"
            genderTextView.text = when (patient.sexo) {
                "Masculino" -> "M"
                "Femenino" -> "F"
                else -> patient.sexo
            }
            therapistTextView.text = if (patient.terapeutaNombre.isNotBlank()) {
                "Terapeuta: ${patient.terapeutaNombre}"
            } else {
                "Sin terapeuta asignado"
            }

            if (!patient.cuidadorNombre.isNullOrBlank()) {
                caregiverTextView.visibility = View.VISIBLE
                caregiverTextView.text = "Cuidador: ${patient.cuidadorNombre}"
            } else {
                caregiverTextView.visibility = View.GONE
            }

            statusTextView.visibility = View.VISIBLE
            if (patient.activo) {
                statusTextView.text = "Activo"
                statusTextView.setBackgroundResource(R.drawable.status_active_background)
            } else {
                statusTextView.text = "Inactivo"
                statusTextView.setBackgroundResource(R.drawable.status_inactive_background)
            }

            editButton.visibility = View.GONE
            exportButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
            rippleOverlay.isClickable = false
            rippleOverlay.isFocusable = false

            ImageUtils.loadUserPhoto(itemView.context, patient.fotoMovil, photoImageView, R.drawable.ic_patient_placeholder)
        }

        private fun getDocumentTypeAbbreviation(documentType: String): String {
            return when (documentType.lowercase()) {
                "dni" -> "DNI"
                "pasaporte" -> "PAS"
                "carnet de extranjería" -> "CE"
                else -> documentType.take(3).uppercase()
            }
        }
    }

    inner class CaregiversPagedAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<CaregiversViewHolder>() {
        private var allCaregivers: List<com.example.serious_game_usil.data.Caregiver> = emptyList()
        private var displayedCaregivers: List<com.example.serious_game_usil.data.Caregiver> = emptyList()

        fun setCaregivers(caregivers: List<com.example.serious_game_usil.data.Caregiver>) {
            allCaregivers = caregivers
            updatePage(currentCaregiverPage, itemsPerPage)
        }

        fun updatePage(page: Int, itemsPerPage: Int) {
            val startIndex = (page - 1) * itemsPerPage
            val endIndex = kotlin.math.min(startIndex + itemsPerPage, allCaregivers.size)
            displayedCaregivers = if (startIndex < allCaregivers.size) {
                allCaregivers.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            notifyDataSetChanged()
        }

        fun getTotalCount(): Int = allCaregivers.size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CaregiversViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_caregiver, parent, false)
            return CaregiversViewHolder(view)
        }

        override fun onBindViewHolder(holder: CaregiversViewHolder, position: Int) {
            val caregiver = displayedCaregivers[position]
            holder.bind(caregiver)
        }

        override fun getItemCount(): Int = displayedCaregivers.size
    }

    inner class CaregiversViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val photoImageView: android.widget.ImageView = itemView.findViewById(R.id.ivCaregiverPhoto)
        private val nameTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverName)
        private val documentTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverDocument)
        private val emailTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverEmail)
        private val phoneTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverPhone)
        private val genderTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverGender)
        private val statusTextView: android.widget.TextView = itemView.findViewById(R.id.tvCaregiverStatus)
        private val patientsButton: android.widget.TextView = itemView.findViewById(R.id.btnPatientsCount)
        private val lastAccessTextView: android.widget.TextView = itemView.findViewById(R.id.tvLastAccess)
        private val editButton: View = itemView.findViewById(R.id.btnEditCaregiver)
        private val viewPatientsButton: View = itemView.findViewById(R.id.btnViewPatients)
        private val deleteButton: View = itemView.findViewById(R.id.btnDeleteCaregiver)
        private val rippleOverlay: View = itemView.findViewById(R.id.rippleOverlay)

        fun bind(caregiver: com.example.serious_game_usil.data.Caregiver) {
            nameTextView.text = caregiver.nombresApellidos
            documentTextView.text = "${getDocumentTypeAbbreviation(caregiver.tipoDocumento)}: ${caregiver.numDocumento}"
            emailTextView.text = caregiver.correo
            phoneTextView.text = caregiver.telefono ?: "No especificado"
            genderTextView.text = when (caregiver.sexo) {
                "Masculino" -> "M"
                "Femenino" -> "F"
                else -> caregiver.sexo
            }

            if (caregiver.activo) {
                statusTextView.text = "Activo"
                statusTextView.setBackgroundResource(R.drawable.status_active_background)
            } else {
                statusTextView.text = "Inactivo"
                statusTextView.setBackgroundResource(R.drawable.status_inactive_background)
            }

            patientsButton.text = when (caregiver.pacientesAsignados) {
                0 -> "Sin pacientes"
                1 -> "1 paciente"
                else -> "${caregiver.pacientesAsignados} pacientes"
            }

            lastAccessTextView.text = if (!caregiver.fechaUltimoAcceso.isNullOrBlank()) {
                caregiver.fechaUltimoAcceso.substringBefore("T").replace("-", "/")
            } else {
                "Sin acceso"
            }

            editButton.visibility = View.GONE
            viewPatientsButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
            rippleOverlay.isClickable = false
            rippleOverlay.isFocusable = false

            ImageUtils.loadUserPhoto(itemView.context, caregiver.fotoMovil, photoImageView, R.drawable.ic_person_placeholder)
        }

        private fun getDocumentTypeAbbreviation(documentType: String): String {
            return when (documentType.lowercase()) {
                "dni" -> "DNI"
                "pasaporte" -> "PAS"
                "carnet de extranjería" -> "CE"
                else -> documentType.take(3).uppercase()
            }
        }
    }
}

class ProgressStatsFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return inflater.inflate(R.layout.fragment_progress_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewModel = ViewModelProvider(requireActivity())[ProgressViewModel::class.java]
        val totalSessionsView = view.findViewById<android.widget.TextView>(R.id.totalSessions)
        val avgProgressView = view.findViewById<android.widget.TextView>(R.id.avgProgress)
        val supportTextView = view.findViewById<android.widget.TextView>(R.id.statsSupportText)

        viewModel.allChildrenProgress.observe(viewLifecycleOwner) { progressList ->
            val totalSessions = progressList.sumOf { it.summary.totalSessions3M }

            val availableScores = progressList.mapNotNull { comparison ->
                comparison.months.firstOrNull { month -> month.totalSessions > 0 }?.overallScore
            }

            val averageProgress = if (availableScores.isNotEmpty()) {
                availableScores.average().toInt()
            } else {
                0
            }

            totalSessionsView.text = totalSessions.toString()
            avgProgressView.text = "$averageProgress%"

            supportTextView.text = if (progressList.isEmpty()) {
                "Aún no hay información consolidada para mostrar indicadores reales."
            } else {
                "${progressList.size} pacientes considerados en este resumen. Los valores se calculan con sesiones reales registradas."
            }
        }
    }
}
