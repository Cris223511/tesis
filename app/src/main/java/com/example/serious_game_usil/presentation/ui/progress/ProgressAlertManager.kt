package com.example.serious_game_usil.presentation.ui.progress

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ThreeMonthComparison
import com.example.serious_game_usil.data.TrendDirection
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProgressAlertManager {
    
    companion object {
        fun checkAndShowAlerts(context: Context, progressList: List<ThreeMonthComparison>) {
            val alerts = generateAlerts(progressList)
            if (alerts.isNotEmpty()) {
                showAlertsDialog(context, alerts)
            }
        }
        
        private fun generateAlerts(progressList: List<ThreeMonthComparison>): List<ProgressAlert> {
            val alerts = mutableListOf<ProgressAlert>()
            
            for (comparison in progressList) {
                val childName = comparison.childName
                val currentMonth = comparison.currentMonth
                
                if (currentMonth == null) continue
                
                // Verificar regresión significativa
                val overallTrend = comparison.summary.overallTrend
                if (overallTrend == "declining") {
                    alerts.add(
                        ProgressAlert(
                            type = AlertType.REGRESSION,
                            childName = childName,
                            message = "muestra una tendencia de regresión en las últimas semanas. Se recomienda revisar el plan terapéutico.",
                            priority = AlertPriority.HIGH
                        )
                    )
                }
                
                // Verificar mejoras significativas
                if (overallTrend == "improving") {
                    val avgProgress = (currentMonth.avgSocialInteraction + 
                                    currentMonth.avgCommunication + 
                                    currentMonth.avgSensoryProcessing + 
                                    currentMonth.avgAttentionFocus + 
                                    currentMonth.avgEmotionalRegulation) / 5.0
                    
                    if (avgProgress >= 80) {
                        alerts.add(
                            ProgressAlert(
                                type = AlertType.IMPROVEMENT,
                                childName = childName,
                                message = "ha mostrado mejoras excelentes (${avgProgress.toInt()}% promedio). ¡Continúa con el buen trabajo!",
                                priority = AlertPriority.POSITIVE
                            )
                        )
                    }
                }
                
                // Verificar falta de sesiones
                if (comparison.summary.totalSessions3M == 0) {
                    alerts.add(
                        ProgressAlert(
                            type = AlertType.NO_SESSIONS,
                            childName = childName,
                            message = "no tiene sesiones registradas en los últimos 3 meses. Es importante mantener la constancia.",
                            priority = AlertPriority.MEDIUM
                        )
                    )
                } else if (comparison.summary.totalSessions3M < 12) { // Menos de 1 por semana
                    alerts.add(
                        ProgressAlert(
                            type = AlertType.LOW_FREQUENCY,
                            childName = childName,
                            message = "tiene pocas sesiones registradas (${comparison.summary.totalSessions3M} en 3 meses). Se recomienda aumentar la frecuencia.",
                            priority = AlertPriority.MEDIUM
                        )
                    )
                }
                
                // Verificar áreas específicas que necesitan atención
                currentMonth.let { metrics ->
                    val lowAreas = mutableListOf<String>()
                    
                    if (metrics.avgSocialInteraction < 40) lowAreas.add("Interacción Social")
                    if (metrics.avgCommunication < 40) lowAreas.add("Comunicación")
                    if (metrics.avgSensoryProcessing < 40) lowAreas.add("Procesamiento Sensorial")
                    if (metrics.avgAttentionFocus < 40) lowAreas.add("Atención y Concentración")
                    if (metrics.avgEmotionalRegulation < 40) lowAreas.add("Regulación Emocional")
                    
                    if (lowAreas.isNotEmpty()) {
                        alerts.add(
                            ProgressAlert(
                                type = AlertType.FOCUS_AREAS,
                                childName = childName,
                                message = "necesita mayor trabajo en: ${lowAreas.joinToString(", ")}. Considera actividades específicas para estas áreas.",
                                priority = AlertPriority.MEDIUM
                            )
                        )
                    }
                }
            }
            
            return alerts.sortedBy { it.priority.order }
        }
        
        private fun showAlertsDialog(context: Context, alerts: List<ProgressAlert>) {
            val message = buildAlertMessage(alerts)
            
            MaterialAlertDialogBuilder(context)
                .setTitle("📊 Alertas de Progreso")
                .setMessage(message)
                .setPositiveButton("Entendido") { dialog, _ -> 
                    dialog.dismiss() 
                }
                .setNeutralButton("Ver Detalles") { dialog, _ ->
                    // Abrir vista detallada
                    dialog.dismiss()
                    // Aquí se podría navegar a más detalles
                }
                .show()
        }
        
        private fun buildAlertMessage(alerts: List<ProgressAlert>): String {
            val grouped = alerts.groupBy { it.type }
            val sb = StringBuilder()
            
            // Alertas de alta prioridad primero
            grouped[AlertType.REGRESSION]?.let { regressionAlerts ->
                sb.append("⚠️ REGRESIONES:\n")
                regressionAlerts.forEach { alert ->
                    sb.append("• ${alert.childName} ${alert.message}\n")
                }
                sb.append("\n")
            }
            
            // Mejoras
            grouped[AlertType.IMPROVEMENT]?.let { improvementAlerts ->
                sb.append("🎉 MEJORAS:\n")
                improvementAlerts.forEach { alert ->
                    sb.append("• ${alert.childName} ${alert.message}\n")
                }
                sb.append("\n")
            }
            
            // Falta de sesiones
            grouped[AlertType.NO_SESSIONS]?.let { noSessionAlerts ->
                sb.append("📅 SIN SESIONES:\n")
                noSessionAlerts.forEach { alert ->
                    sb.append("• ${alert.childName} ${alert.message}\n")
                }
                sb.append("\n")
            }
            
            // Baja frecuencia
            grouped[AlertType.LOW_FREQUENCY]?.let { lowFreqAlerts ->
                sb.append("⏰ BAJA FRECUENCIA:\n")
                lowFreqAlerts.forEach { alert ->
                    sb.append("• ${alert.childName} ${alert.message}\n")
                }
                sb.append("\n")
            }
            
            // Áreas de enfoque
            grouped[AlertType.FOCUS_AREAS]?.let { focusAlerts ->
                sb.append("🎯 ÁREAS DE ENFOQUE:\n")
                focusAlerts.forEach { alert ->
                    sb.append("• ${alert.childName} ${alert.message}\n")
                }
            }
            
            return sb.toString().trim()
        }
    }
}

data class ProgressAlert(
    val type: AlertType,
    val childName: String,
    val message: String,
    val priority: AlertPriority
)

enum class AlertType {
    REGRESSION,      // Regresión significativa
    IMPROVEMENT,     // Mejora notable
    NO_SESSIONS,     // Sin sesiones
    LOW_FREQUENCY,   // Pocas sesiones
    FOCUS_AREAS      // Áreas que necesitan atención
}

enum class AlertPriority(val order: Int) {
    HIGH(1),         // Regresiones
    MEDIUM(2),       // Frecuencia y áreas específicas
    POSITIVE(3)      // Mejoras (mostrar al final)
}