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
    private var requestedChildId: Int = 0

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
        
        requestedChildId = arguments?.getInt("child_id") ?: 0
        if (requestedChildId > 0) {
            viewModel.loadThreeMonthComparison(requestedChildId)
        } else {
            viewModel.loadAllChildrenProgress()
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(requireActivity())[ProgressViewModel::class.java]
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

        viewModel.allChildrenProgress.observe(viewLifecycleOwner) { progressList ->
            if (requestedChildId == 0) {
                val bestCandidate = progressList.firstOrNull { comparison ->
                    comparison.summary.totalSessions3M > 0 || comparison.months.any { month -> month.totalSessions > 0 }
                } ?: progressList.firstOrNull()

                bestCandidate?.let { updateUI(it) }
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
            binding.textNoData.text = "No hay datos suficientes para comparar.\nSe necesitan al menos 2 meses con sesiones registradas."
            binding.layoutComparison.visibility = View.GONE
        } else {
            binding.textNoData.visibility = View.GONE
            binding.layoutComparison.visibility = View.VISIBLE
        }

        monthsAdapter.updateMonths(months)

        val monthCards = listOf(
            binding.cardMonth1,
            binding.cardMonth2,
            binding.cardMonth3
        )

        val relevantMonths = months.filter { it.totalSessions > 0 }.take(3)

        relevantMonths.forEachIndexed { index, month ->
            updateMonthCard(monthCards[index], month, index == 0, index)
        }

        for (i in relevantMonths.size until monthCards.size) {
            monthCards[i].visibility = View.GONE
        }

        if (relevantMonths.size >= 2) {
            showComparisonAnalysis(relevantMonths)
        }
    }

    private fun showComparisonAnalysis(months: List<MonthlyProgress>) {
        if (months.size >= 2) {
            val currentMonth = months[0]
            val previousMonth = months[1]

            val improvement = currentMonth.overallScore - previousMonth.overallScore
            val improvementText = when {
                improvement > 10f -> "Mejora significativa (+${improvement.toInt()}%)"
                improvement > 0f -> "Mejora moderada (+${improvement.toInt()}%)"
                kotlin.math.abs(improvement) < 0.01f -> "Sin cambios"
                improvement > -10f -> "Leve descenso (${improvement.toInt()}%)"
                else -> "Descenso importante (${improvement.toInt()}%)"
            }

            binding.textOverallTrend.text = improvementText
            binding.textOverallTrend.visibility = View.VISIBLE
        }
    }

    private fun updateMonthCard(card: MaterialCardView, month: MonthlyProgress, isCurrentMonth: Boolean, position: Int = 0) {
        card.visibility = View.VISIBLE

        val backgroundColor = when {
            isCurrentMonth -> R.color.primary_light
            month.overallScore >= 75 -> R.color.success_color
            month.overallScore >= 50 -> R.color.warning_color
            else -> R.color.error_color
        }

        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
        card.strokeWidth = if (isCurrentMonth) 3 else 1
        card.strokeColor = ContextCompat.getColor(requireContext(), R.color.primary)

        val monthText = card.findViewById<android.widget.TextView>(R.id.textMonth)
        val scoreText = card.findViewById<android.widget.TextView>(R.id.textScore)
        val sessionsText = card.findViewById<android.widget.TextView>(R.id.textSessions)
        val progressBar: LinearProgressIndicator? = null

        monthText?.text = formatMonthName(month.month)

        scoreText?.text = String.format("%.1f%%", month.overallScore)
        scoreText?.textSize = if (isCurrentMonth) 20f else 18f

        sessionsText?.text = when(month.totalSessions) {
            0 -> "Sin sesiones"
            1 -> "1 sesión"
            else -> "${month.totalSessions} sesiones"
        }

        progressBar?.progress = month.overallScore.toInt()

        card.scaleX = 0.9f
        card.scaleY = 0.9f
        card.alpha = 0f
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(position * 150L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun formatMonthName(monthString: String): String {
        return try {
            val parts = monthString.split(" ")
            if (parts.size >= 2) {
                "${parts[0].take(3).uppercase()} ${parts[1]}"
            } else {
                monthString
            }
        } catch (e: Exception) {
            monthString
        }
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
            "improving" -> "Progreso destacado"
            "stable" -> "Avance constante"
            "declining" -> "Necesita refuerzo"
            else -> "Seguimiento en curso"
        }

        binding.textTotalSessions.text = "${summary.totalSessions3M} sesiones en 3 meses"
        binding.textAvgSessionTime.text = "Promedio por sesión: ${summary.avgSessionTime} min"

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
