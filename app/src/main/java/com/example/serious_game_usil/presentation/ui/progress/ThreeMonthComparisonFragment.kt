package com.example.serious_game_usil.presentation.ui.progress

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.*
import com.example.serious_game_usil.databinding.FragmentThreeMonthComparisonBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class ThreeMonthComparisonFragment : Fragment() {

    private var _binding: FragmentThreeMonthComparisonBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ProgressViewModel
    private lateinit var monthsAdapter: MonthlyProgressAdapter
    private lateinit var areasAdapter: ProgressAreasAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThreeMonthComparisonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerViews()
        setupObservers()
        
        // Obtener ID del niño desde argumentos
        val childId = arguments?.getInt("child_id") ?: 0
        if (childId > 0) {
            viewModel.loadThreeMonthComparison(childId)
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ProgressViewModel::class.java]
    }

    private fun setupRecyclerViews() {
        // Configurar RecyclerView para los meses
        monthsAdapter = MonthlyProgressAdapter { monthProgress ->
            // Click en un mes específico para ver detalles
            showMonthDetails(monthProgress)
        }
        
        binding.recyclerViewMonths.apply {
            adapter = monthsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        // Configurar RecyclerView para las áreas de progreso
        areasAdapter = ProgressAreasAdapter()
        binding.recyclerViewAreas.apply {
            adapter = areasAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupObservers() {
        viewModel.threeMonthComparison.observe(viewLifecycleOwner) { comparison ->
            comparison?.let {
                updateUI(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
            }
        }
    }

    private fun updateUI(comparison: ThreeMonthComparison) {
        // Actualizar información del niño
        binding.textChildName.text = comparison.childName
        
        // Actualizar comparativa de meses
        updateMonthsComparison(comparison.months)
        
        // Actualizar progreso por áreas
        updateAreasProgress(comparison.currentMonth)
        
        // Actualizar resumen
        updateSummary(comparison.summary)
        
        // Actualizar recomendaciones
        updateRecommendations(comparison.recommendations)
    }

    private fun updateMonthsComparison(months: List<MonthlyProgress>) {
        if (months.isEmpty() || months.all { it.totalSessions == 0 }) {
            binding.textNoData.visibility = View.VISIBLE
            binding.layoutComparison.visibility = View.VISIBLE // Mostrar layout con 0s
        } else {
            binding.textNoData.visibility = View.GONE
            binding.layoutComparison.visibility = View.VISIBLE
        }

        // Actualizar adapter
        monthsAdapter.updateMonths(months)

        // Crear cards para los 3 meses (máximo)
        val monthCards = listOf(
            binding.cardMonth1,
            binding.cardMonth2, 
            binding.cardMonth3
        )

        months.take(3).forEachIndexed { index, month ->
            updateMonthCard(monthCards[index], month, index == 0, index)
        }

        // Ocultar cards no utilizadas
        for (i in months.size until monthCards.size) {
            monthCards[i].visibility = View.GONE
        }
    }

    private fun updateMonthCard(card: MaterialCardView, month: MonthlyProgress, isCurrentMonth: Boolean, position: Int = 0) {
        card.visibility = View.VISIBLE
        
        // Configurar colores según el progreso y si es el mes actual
        val backgroundColor = when {
            isCurrentMonth -> R.color.primary
            month.totalSessions == 0 -> android.R.color.darker_gray // Color gris para sin datos
            month.overallScore >= 80 -> R.color.green_500
            month.overallScore >= 60 -> R.color.orange
            else -> R.color.red_500
        }
        
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
        
        // Encontrar TextViews dentro de la card (esto dependería del layout)
        val monthText = card.findViewById<android.widget.TextView>(R.id.textMonth)
        val scoreText = card.findViewById<android.widget.TextView>(R.id.textScore)
        val sessionsText = card.findViewById<android.widget.TextView>(R.id.textSessions)
        val trendIcon = card.findViewById<android.widget.ImageView>(R.id.iconTrend)
        
        monthText?.text = month.month
        scoreText?.text = if (month.totalSessions == 0) "Sin datos" else "${month.overallScore}"
        sessionsText?.text = if (month.totalSessions == 0) "0 sesiones" else "${month.totalSessions} sesiones"
        
        // Configurar icono de tendencia
        val trendIconRes = when(month.trend) {
            "improving" -> android.R.drawable.arrow_up_float
            "declining" -> android.R.drawable.arrow_down_float  
            else -> android.R.drawable.ic_menu_view
        }
        trendIcon?.setImageResource(trendIconRes)
        
        // Animación del card
        card.alpha = 0f
        card.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(position * 100L)
            .start()
    }

    private fun updateAreasProgress(currentMonth: AutismProgressMetrics?) {
        currentMonth?.let { metrics ->
            val areas = listOf(
                ProgressBarData(
                    label = "Interacción Social",
                    currentValue = metrics.avgSocialInteraction.toInt(),
                    previousValue = 0, // Se calcularía vs mes anterior
                    colorRes = R.color.stat_blue,
                    trend = TrendDirection.STABLE,
                    description = "Habilidades de interacción con otros"
                ),
                ProgressBarData(
                    label = "Comunicación",
                    currentValue = metrics.avgCommunication.toInt(),
                    previousValue = 0,
                    colorRes = R.color.stat_pink,
                    trend = TrendDirection.STABLE,
                    description = "Expresión verbal y no verbal"
                ),
                ProgressBarData(
                    label = "Procesamiento Sensorial",
                    currentValue = metrics.avgSensoryProcessing.toInt(),
                    previousValue = 0,
                    colorRes = R.color.activity_green,
                    trend = TrendDirection.STABLE,
                    description = "Respuesta a estímulos sensoriales"
                ),
                ProgressBarData(
                    label = "Atención y Concentración",
                    currentValue = metrics.avgAttentionFocus.toInt(),
                    previousValue = 0,
                    colorRes = R.color.activity_blue,
                    trend = TrendDirection.STABLE,
                    description = "Capacidad de mantener el foco"
                ),
                ProgressBarData(
                    label = "Regulación Emocional",
                    currentValue = metrics.avgEmotionalRegulation.toInt(),
                    previousValue = 0,
                    colorRes = R.color.activity_pink,
                    trend = TrendDirection.STABLE,
                    description = "Manejo de emociones y frustración"
                )
            )
            
            areasAdapter.updateAreas(areas)
        }
    }

    private fun updateSummary(summary: ProgressSummary) {
        binding.textOverallTrend.text = when(summary.overallTrend) {
            "improving" -> "¡Progreso excelente!"
            "stable" -> "Progreso constante"
            "declining" -> "Requiere atención"
            else -> "En desarrollo"
        }

        binding.textTotalSessions.text = "${summary.totalSessions3M} sesiones en 3 meses"
        binding.textAvgSessionTime.text = "Promedio: ${summary.avgSessionTime} min por sesión"

        // Configurar color del trend
        val trendColor = when(summary.overallTrend) {
            "improving" -> R.color.green_500
            "declining" -> R.color.red_500
            else -> R.color.primary
        }
        binding.textOverallTrend.setTextColor(ContextCompat.getColor(requireContext(), trendColor))
    }

    private fun updateRecommendations(recommendations: List<String>) {
        if (recommendations.isEmpty()) {
            binding.layoutRecommendations.visibility = View.GONE
            return
        }

        binding.layoutRecommendations.visibility = View.VISIBLE
        
        // Mostrar las primeras 3 recomendaciones
        val recommendationTexts = listOf(
            binding.textRecommendation1,
            binding.textRecommendation2,
            binding.textRecommendation3
        )

        recommendations.take(3).forEachIndexed { index, recommendation ->
            recommendationTexts[index].text = "• $recommendation"
            recommendationTexts[index].visibility = View.VISIBLE
        }

        // Ocultar TextViews no utilizados
        for (i in recommendations.size until recommendationTexts.size) {
            recommendationTexts[i].visibility = View.GONE
        }
    }

    private fun showMonthDetails(monthProgress: MonthlyProgress) {
        // Mostrar diálogo o navegar a pantalla de detalles del mes
        // Implementar según necesidades
    }

    private fun showError(error: String) {
        binding.textError.text = error
        binding.textError.visibility = View.VISIBLE
        binding.layoutComparison.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(childId: Int): ThreeMonthComparisonFragment {
            return ThreeMonthComparisonFragment().apply {
                arguments = Bundle().apply {
                    putInt("child_id", childId)
                }
            }
        }
    }
}