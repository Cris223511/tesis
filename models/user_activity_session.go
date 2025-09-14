package models

import (
	"time"
	"gorm.io/gorm"
)

// UserActivitySession - Registro de cada sesión de actividad realizada
type UserActivitySession struct {
	ID          uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID      uint      `gorm:"not null;index" json:"user_id"`
	ActivityID  uint      `gorm:"not null;index" json:"activity_id"`
	
	// Información de la sesión
	StartTime   time.Time `gorm:"not null;index" json:"start_time"`
	EndTime     *time.Time `gorm:"index" json:"end_time,omitempty"`
	Duration    int       `gorm:"default:0" json:"duration"` // en segundos
	
	// Métricas de rendimiento
	Score       float64   `gorm:"default:0" json:"score"`
	Completed   bool      `gorm:"default:false;index" json:"completed"`
	Progress    float64   `gorm:"default:0" json:"progress"` // 0-100%
	
	// Categorización
	Category    string    `gorm:"size:50;not null;index" json:"category"` // COGNITIVE, MOTOR, etc.
	Difficulty  string    `gorm:"size:20;not null;index" json:"difficulty"` // EASY, MEDIUM, HARD
	
	// Metadatos
	DeviceInfo  string    `gorm:"type:text" json:"device_info,omitempty"`
	Notes       string    `gorm:"type:text" json:"notes,omitempty"`
	
	CreatedAt   time.Time `gorm:"index" json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
	
	// Relaciones
	User        Usuarios  `gorm:"foreignKey:UserID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
}

// UserMonthlyStats - Estadísticas mensuales agregadas para optimizar consultas
type UserMonthlyStats struct {
	ID               uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID           uint      `gorm:"not null;index:idx_user_month" json:"user_id"`
	Year             int       `gorm:"not null;index:idx_user_month" json:"year"`
	Month            int       `gorm:"not null;index:idx_user_month" json:"month"` // 1-12
	
	// Métricas agregadas
	TotalSessions    int       `gorm:"default:0" json:"total_sessions"`
	CompletedSessions int      `gorm:"default:0" json:"completed_sessions"`
	TotalDuration    int       `gorm:"default:0" json:"total_duration"` // en segundos
	AverageScore     float64   `gorm:"default:0" json:"average_score"`
	AverageProgress  float64   `gorm:"default:0" json:"average_progress"`
	
	// Distribución por categoría
	CognitiveCount   int       `gorm:"default:0" json:"cognitive_count"`
	MotorCount       int       `gorm:"default:0" json:"motor_count"`
	SocialCount      int       `gorm:"default:0" json:"social_count"`
	EmotionalCount   int       `gorm:"default:0" json:"emotional_count"`
	SensoryCount     int       `gorm:"default:0" json:"sensory_count"`
	
	// Distribución por dificultad
	EasyCount        int       `gorm:"default:0" json:"easy_count"`
	MediumCount      int       `gorm:"default:0" json:"medium_count"`
	HardCount        int       `gorm:"default:0" json:"hard_count"`
	
	// Control de versiones
	LastUpdated      time.Time `gorm:"index" json:"last_updated"`
	CreatedAt        time.Time `gorm:"index" json:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"`
	
	// Relaciones
	User             Usuarios  `gorm:"foreignKey:UserID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"-"`
}

// Índice compuesto para optimizar consultas por usuario y mes
func (UserMonthlyStats) TableName() string {
	return "user_monthly_stats"
}

// BeforeCreate - Hook para validar datos antes de crear
func (s *UserActivitySession) BeforeCreate(tx *gorm.DB) error {
	if s.StartTime.IsZero() {
		s.StartTime = time.Now()
	}
	return nil
}

// CalculateDuration - Calcula la duración de la sesión
func (s *UserActivitySession) CalculateDuration() {
	if s.EndTime != nil && !s.StartTime.IsZero() {
		s.Duration = int(s.EndTime.Sub(s.StartTime).Seconds())
	}
}

// GetCompletionRate - Calcula el porcentaje de finalización del mes
func (m *UserMonthlyStats) GetCompletionRate() float64 {
	if m.TotalSessions == 0 {
		return 0
	}
	return (float64(m.CompletedSessions) / float64(m.TotalSessions)) * 100
}