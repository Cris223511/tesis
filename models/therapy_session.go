package models

import (
	"time"
)

// TherapySession - Sesión terapéutica de serious game para niños autistas
type TherapySession struct {
	ID          uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	ChildID     uint      `gorm:"not null;index" json:"child_id"` // ID del niño
	TherapistID uint      `gorm:"index" json:"therapist_id,omitempty"` // ID del terapeuta (opcional)
	ParentID    uint      `gorm:"index" json:"parent_id,omitempty"`    // ID del padre (opcional)
	
	// Información de la sesión
	SessionDate time.Time `gorm:"not null;index" json:"session_date"`
	StartTime   time.Time `gorm:"not null" json:"start_time"`
	EndTime     *time.Time `json:"end_time,omitempty"`
	Duration    int       `gorm:"default:0" json:"duration"` // en segundos
	
	// Tipo de actividad terapéutica
	ActivityType      string `gorm:"size:50;not null;index" json:"activity_type"` 
	ActivityName      string `gorm:"size:100;not null" json:"activity_name"`
	DifficultyLevel   int    `gorm:"default:1" json:"difficulty_level"` // 1-5
	
	// Métricas específicas para autismo
	SocialInteraction    int `gorm:"default:0" json:"social_interaction"`    // 0-100
	Communication        int `gorm:"default:0" json:"communication"`         // 0-100
	SensoryProcessing    int `gorm:"default:0" json:"sensory_processing"`    // 0-100
	AttentionFocus       int `gorm:"default:0" json:"attention_focus"`       // 0-100
	EmotionalRegulation  int `gorm:"default:0" json:"emotional_regulation"`  // 0-100
	MotorSkills          int `gorm:"default:0" json:"motor_skills"`          // 0-100
	ProblemSolving       int `gorm:"default:0" json:"problem_solving"`       // 0-100
	
	// Estados durante la sesión
	MeltdownOccurred     bool   `gorm:"default:false" json:"meltdown_occurred"`
	StimBehaviors        int    `gorm:"default:0" json:"stim_behaviors"`      // Cantidad de comportamientos estimulatorios
	TaskCompletion       int    `gorm:"default:0" json:"task_completion"`     // 0-100%
	IndependenceLevel    int    `gorm:"default:0" json:"independence_level"`  // 0-100%
	
	// Observaciones cualitativas
	TherapistNotes       string `gorm:"type:text" json:"therapist_notes,omitempty"`
	ParentNotes          string `gorm:"type:text" json:"parent_notes,omitempty"`
	ChildMood            string `gorm:"size:50" json:"child_mood,omitempty"` // happy, calm, frustrated, etc.
	
	// Tecnología/Ambiente
	DeviceUsed           string `gorm:"size:100" json:"device_used,omitempty"`
	EnvironmentSetting   string `gorm:"size:50" json:"environment_setting,omitempty"` // home, clinic, school
	
	CreatedAt            time.Time `gorm:"index" json:"created_at"`
	UpdatedAt            time.Time `json:"updated_at"`
	
	// Relaciones
	Child                Usuarios `gorm:"foreignKey:ChildID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
}

// AutismProgressMetrics - Métricas mensuales de progreso para autismo
type AutismProgressMetrics struct {
	ID       uint `gorm:"primaryKey;autoIncrement" json:"id"`
	ChildID  uint `gorm:"not null;index:idx_child_month" json:"child_id"`
	Year     int  `gorm:"not null;index:idx_child_month" json:"year"`
	Month    int  `gorm:"not null;index:idx_child_month" json:"month"` // 1-12
	
	// Promedios mensuales por área (0-100)
	AvgSocialInteraction   float64 `json:"avg_social_interaction"`
	AvgCommunication       float64 `json:"avg_communication"`
	AvgSensoryProcessing   float64 `json:"avg_sensory_processing"`
	AvgAttentionFocus      float64 `json:"avg_attention_focus"`
	AvgEmotionalRegulation float64 `json:"avg_emotional_regulation"`
	AvgMotorSkills         float64 `json:"avg_motor_skills"`
	AvgProblemSolving      float64 `json:"avg_problem_solving"`
	
	// Estadísticas de sesiones
	TotalSessions          int     `json:"total_sessions"`
	CompletedSessions      int     `json:"completed_sessions"`
	TotalDuration          int     `json:"total_duration"` // en segundos
	AvgSessionDuration     float64 `json:"avg_session_duration"`
	AvgTaskCompletion      float64 `json:"avg_task_completion"`
	AvgIndependenceLevel   float64 `json:"avg_independence_level"`
	
	// Indicadores de desafíos
	MeltdownCount          int     `json:"meltdown_count"`
	AvgStimBehaviors       float64 `json:"avg_stim_behaviors"`
	
	// Progreso comparativo (vs mes anterior)
	SocialInteractionTrend    float64 `json:"social_interaction_trend"`     // +/- cambio
	CommunicationTrend        float64 `json:"communication_trend"`
	SensoryProcessingTrend    float64 `json:"sensory_processing_trend"`
	AttentionFocusTrend       float64 `json:"attention_focus_trend"`
	EmotionalRegulationTrend  float64 `json:"emotional_regulation_trend"`
	
	// Metadatos
	DataQuality            string    `gorm:"size:20;default:'good'" json:"data_quality"` // excellent, good, fair, poor
	LastCalculated         time.Time `json:"last_calculated"`
	CreatedAt              time.Time `gorm:"index" json:"created_at"`
	UpdatedAt              time.Time `json:"updated_at"`
	
	// Relaciones
	Child                  Usuarios `gorm:"foreignKey:ChildID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
}

// ActivityType constants específicos para autismo
const (
	// Habilidades Sociales
	ACTIVITY_SOCIAL_RECOGNITION = "social_recognition"
	ACTIVITY_EMOTION_READING    = "emotion_reading"
	ACTIVITY_TURN_TAKING        = "turn_taking"
	ACTIVITY_SOCIAL_STORIES     = "social_stories"
	
	// Comunicación
	ACTIVITY_VERBAL_IMITATION   = "verbal_imitation"
	ACTIVITY_PICTURE_EXCHANGE   = "picture_exchange"
	ACTIVITY_GESTURE_LEARNING   = "gesture_learning"
	
	// Procesamiento Sensorial
	ACTIVITY_SOUND_THERAPY      = "sound_therapy"
	ACTIVITY_VISUAL_PROCESSING  = "visual_processing"
	ACTIVITY_TEXTURE_EXPLORATION = "texture_exploration"
	
	// Atención y Concentración
	ACTIVITY_FOCUS_TRAINING     = "focus_training"
	ACTIVITY_ATTENTION_SHIFTING = "attention_shifting"
	ACTIVITY_SUSTAINED_ATTENTION = "sustained_attention"
)

// GetOverallProgress - Calcula progreso general del niño (0-100)
func (m *AutismProgressMetrics) GetOverallProgress() float64 {
	areas := []float64{
		m.AvgSocialInteraction,
		m.AvgCommunication,
		m.AvgSensoryProcessing,
		m.AvgAttentionFocus,
		m.AvgEmotionalRegulation,
		m.AvgMotorSkills,
		m.AvgProblemSolving,
	}
	
	sum := 0.0
	count := 0.0
	for _, score := range areas {
		if score > 0 {
			sum += score
			count++
		}
	}
	
	if count == 0 {
		return 0
	}
	return sum / count
}

// GetProgressTrend - Indica si el progreso general es positivo/negativo
func (m *AutismProgressMetrics) GetProgressTrend() string {
	trends := []float64{
		m.SocialInteractionTrend,
		m.CommunicationTrend,
		m.SensoryProcessingTrend,
		m.AttentionFocusTrend,
		m.EmotionalRegulationTrend,
	}
	
	positive, negative := 0, 0
	for _, trend := range trends {
		if trend > 0 {
			positive++
		} else if trend < 0 {
			negative++
		}
	}
	
	if positive > negative {
		return "improving"
	} else if negative > positive {
		return "declining" 
	}
	return "stable"
}