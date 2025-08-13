package services

import (
	"fmt"
	"time"
	"usuarios/models"
	"gorm.io/gorm"
)

type AutismProgressService struct {
	db *gorm.DB
}

func NewAutismProgressService(db *gorm.DB) *AutismProgressService {
	return &AutismProgressService{db: db}
}

// ThreeMonthComparison - Respuesta para la comparativa de 3 meses
type ThreeMonthComparison struct {
	ChildID      uint                           `json:"child_id"`
	ChildName    string                         `json:"child_name"`
	CurrentMonth *models.AutismProgressMetrics  `json:"current_month"`
	Months       []MonthlyProgress              `json:"months"`
	Summary      ProgressSummary                `json:"summary"`
	Recommendations []string                    `json:"recommendations"`
}

type MonthlyProgress struct {
	Month        string  `json:"month"`         // "Ago", "Jul", "Jun"
	Year         int     `json:"year"`
	OverallScore int     `json:"overall_score"` // 0-100
	Trend        string  `json:"trend"`         // "improving", "stable", "declining"
	TotalSessions int    `json:"total_sessions"`
	// Detalle por área
	Areas        []AreaProgress `json:"areas"`
}

type AreaProgress struct {
	Name  string `json:"name"`
	Score int    `json:"score"` // 0-100
	Trend float64 `json:"trend"` // cambio vs mes anterior
}

type ProgressSummary struct {
	OverallTrend      string   `json:"overall_trend"`
	StrongestAreas    []string `json:"strongest_areas"`
	ImprovingAreas    []string `json:"improving_areas"`
	AreasNeedingWork  []string `json:"areas_needing_work"`
	TotalSessions3M   int      `json:"total_sessions_3m"`
	AvgSessionTime    int      `json:"avg_session_time"` // en minutos
}

// GetThreeMonthComparison - Obtiene la comparativa de los últimos 3 meses
func (s *AutismProgressService) GetThreeMonthComparison(childID uint) (*ThreeMonthComparison, error) {
	// Obtener información del niño
	var child models.Usuarios
	if err := s.db.First(&child, childID).Error; err != nil {
		return nil, fmt.Errorf("child not found: %w", err)
	}

	// Obtener métricas de los últimos 3 meses
	now := time.Now()
	var metrics []models.AutismProgressMetrics
	
	err := s.db.Where("child_id = ?", childID).
		Where("(year = ? AND month >= ?) OR (year = ? AND month <= ?)", 
			now.Year(), int(now.Month())-2, 
			now.Year(), int(now.Month())).
		Order("year DESC, month DESC").
		Limit(3).
		Find(&metrics).Error
	
	if err != nil {
		return nil, fmt.Errorf("failed to get metrics: %w", err)
	}

	// Convertir métricas a formato de respuesta
	months := make([]MonthlyProgress, len(metrics))
	monthNames := []string{"Ene", "Feb", "Mar", "Abr", "May", "Jun", 
		                   "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"}
	
	totalSessions3M := 0
	totalDuration3M := 0

	for i, metric := range metrics {
		monthName := monthNames[metric.Month-1]
		overallScore := int(metric.GetOverallProgress())
		
		months[i] = MonthlyProgress{
			Month:         monthName,
			Year:          metric.Year,
			OverallScore:  overallScore,
			Trend:         metric.GetProgressTrend(),
			TotalSessions: metric.TotalSessions,
			Areas: []AreaProgress{
				{"Interacción Social", int(metric.AvgSocialInteraction), metric.SocialInteractionTrend},
				{"Comunicación", int(metric.AvgCommunication), metric.CommunicationTrend},
				{"Procesamiento Sensorial", int(metric.AvgSensoryProcessing), metric.SensoryProcessingTrend},
				{"Atención y Concentración", int(metric.AvgAttentionFocus), metric.AttentionFocusTrend},
				{"Regulación Emocional", int(metric.AvgEmotionalRegulation), metric.EmotionalRegulationTrend},
			},
		}
		
		totalSessions3M += metric.TotalSessions
		totalDuration3M += metric.TotalDuration
	}

	// Generar resumen
	summary := s.generateSummary(metrics)
	summary.TotalSessions3M = totalSessions3M
	if totalSessions3M > 0 {
		summary.AvgSessionTime = (totalDuration3M / totalSessions3M) / 60 // convertir a minutos
	}

	// Generar recomendaciones
	recommendations := s.generateRecommendations(metrics)

	var currentMonth *models.AutismProgressMetrics
	if len(metrics) > 0 {
		currentMonth = &metrics[0] // El primer elemento es el más reciente
	}

	return &ThreeMonthComparison{
		ChildID:      childID,
		ChildName:    child.Nombres_Apellidos,
		CurrentMonth: currentMonth,
		Months:       months,
		Summary:      summary,
		Recommendations: recommendations,
	}, nil
}

// generateSummary - Genera un resumen del progreso
func (s *AutismProgressService) generateSummary(metrics []models.AutismProgressMetrics) ProgressSummary {
	if len(metrics) == 0 {
		return ProgressSummary{}
	}

	latest := metrics[0]
	
	// Determinar tendencia general
	overallTrend := latest.GetProgressTrend()
	
	// Identificar áreas más fuertes (>75)
	strongestAreas := []string{}
	improvingAreas := []string{}
	needsWork := []string{}
	
	areas := map[string]struct{Score float64; Trend float64}{
		"Interacción Social":      {latest.AvgSocialInteraction, latest.SocialInteractionTrend},
		"Comunicación":           {latest.AvgCommunication, latest.CommunicationTrend},
		"Procesamiento Sensorial": {latest.AvgSensoryProcessing, latest.SensoryProcessingTrend},
		"Atención y Concentración": {latest.AvgAttentionFocus, latest.AttentionFocusTrend},
		"Regulación Emocional":   {latest.AvgEmotionalRegulation, latest.EmotionalRegulationTrend},
	}
	
	for name, data := range areas {
		if data.Score >= 75 {
			strongestAreas = append(strongestAreas, name)
		}
		if data.Trend > 5 { // Mejora significativa
			improvingAreas = append(improvingAreas, name)
		}
		if data.Score < 50 {
			needsWork = append(needsWork, name)
		}
	}
	
	return ProgressSummary{
		OverallTrend:     overallTrend,
		StrongestAreas:   strongestAreas,
		ImprovingAreas:   improvingAreas,
		AreasNeedingWork: needsWork,
	}
}

// generateRecommendations - Genera recomendaciones basadas en el progreso
func (s *AutismProgressService) generateRecommendations(metrics []models.AutismProgressMetrics) []string {
	if len(metrics) == 0 {
		return []string{}
	}
	
	latest := metrics[0]
	recommendations := []string{}
	
	// Recomendaciones basadas en áreas débiles
	if latest.AvgSocialInteraction < 50 {
		recommendations = append(recommendations, "Incrementar actividades de interacción social y juego cooperativo")
	}
	if latest.AvgCommunication < 50 {
		recommendations = append(recommendations, "Enfocarse en ejercicios de comunicación verbal y no verbal")
	}
	if latest.AvgSensoryProcessing < 50 {
		recommendations = append(recommendations, "Incluir más actividades de integración sensorial")
	}
	if latest.AvgAttentionFocus < 50 {
		recommendations = append(recommendations, "Trabajar en ejercicios de atención sostenida y concentración")
	}
	
	// Recomendaciones basadas en tendencias negativas
	if latest.SocialInteractionTrend < -10 {
		recommendations = append(recommendations, "Revisar estrategias de interacción social - posible regresión")
	}
	
	// Recomendaciones positivas
	if latest.GetProgressTrend() == "improving" {
		recommendations = append(recommendations, "¡Excelente progreso! Mantener la consistencia en las sesiones")
	}
	
	if len(recommendations) == 0 {
		recommendations = append(recommendations, "Continuar con el plan terapéutico actual")
	}
	
	return recommendations
}

// RecordTherapySession - Registra una nueva sesión terapéutica
func (s *AutismProgressService) RecordTherapySession(session *models.TherapySession) error {
	// Calcular duración si no está establecida
	if session.EndTime != nil && !session.StartTime.IsZero() {
		duration := int(session.EndTime.Sub(session.StartTime).Seconds())
		session.Duration = duration
	}
	
	// Guardar la sesión
	if err := s.db.Create(session).Error; err != nil {
		return fmt.Errorf("failed to record session: %w", err)
	}
	
	// Actualizar métricas mensuales de forma asíncrona
	go s.updateMonthlyMetrics(session.ChildID, session.SessionDate.Year(), int(session.SessionDate.Month()))
	
	return nil
}

// updateMonthlyMetrics - Actualiza las métricas mensuales agregadas
func (s *AutismProgressService) updateMonthlyMetrics(childID uint, year, month int) {
	// Lógica para recalcular métricas mensuales
	// Esto se ejecutaría de forma asíncrona para no impactar el rendimiento
	
	var sessions []models.TherapySession
	startDate := time.Date(year, time.Month(month), 1, 0, 0, 0, 0, time.UTC)
	endDate := startDate.AddDate(0, 1, 0).Add(-time.Second)
	
	s.db.Where("child_id = ? AND session_date BETWEEN ? AND ?", childID, startDate, endDate).
		Find(&sessions)
	
	if len(sessions) == 0 {
		return
	}
	
	// Calcular promedios
	var totalSocial, totalComm, totalSensory, totalAttention, totalEmotional float64
	var totalDuration, completedSessions, meltdownCount int
	var totalStimBehaviors, totalTaskCompletion, totalIndependence float64
	
	for _, session := range sessions {
		totalSocial += float64(session.SocialInteraction)
		totalComm += float64(session.Communication)
		totalSensory += float64(session.SensoryProcessing)
		totalAttention += float64(session.AttentionFocus)
		totalEmotional += float64(session.EmotionalRegulation)
		totalDuration += session.Duration
		totalStimBehaviors += float64(session.StimBehaviors)
		totalTaskCompletion += float64(session.TaskCompletion)
		totalIndependence += float64(session.IndependenceLevel)
		
		if session.TaskCompletion >= 80 {
			completedSessions++
		}
		if session.MeltdownOccurred {
			meltdownCount++
		}
	}
	
	sessionCount := float64(len(sessions))
	
	metrics := models.AutismProgressMetrics{
		ChildID:                childID,
		Year:                   year,
		Month:                  month,
		AvgSocialInteraction:   totalSocial / sessionCount,
		AvgCommunication:       totalComm / sessionCount,
		AvgSensoryProcessing:   totalSensory / sessionCount,
		AvgAttentionFocus:      totalAttention / sessionCount,
		AvgEmotionalRegulation: totalEmotional / sessionCount,
		TotalSessions:          len(sessions),
		CompletedSessions:      completedSessions,
		TotalDuration:          totalDuration,
		AvgSessionDuration:     float64(totalDuration) / sessionCount,
		AvgTaskCompletion:      totalTaskCompletion / sessionCount,
		AvgIndependenceLevel:   totalIndependence / sessionCount,
		MeltdownCount:          meltdownCount,
		AvgStimBehaviors:       totalStimBehaviors / sessionCount,
		LastCalculated:         time.Now(),
	}
	
	// Calcular tendencias comparando con el mes anterior
	// ... (lógica para calcular tendencias)
	
	// Usar UPSERT para actualizar o crear
	s.db.Where("child_id = ? AND year = ? AND month = ?", childID, year, month).
		Assign(&metrics).
		FirstOrCreate(&metrics)
}