package models

import (
	"time"
)


type ReportHistory struct {
	ID                  uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	PacienteID          uint      `gorm:"not null;index" json:"paciente_id"`
	GeneratedByUserID   uint      `gorm:"not null;index" json:"generated_by_user_id"`
	ReportType          string    `gorm:"size:50;not null;index" json:"report_type"` 
	SessionID           *uint     `gorm:"index" json:"session_id,omitempty"`

	ReportTitle         string    `gorm:"size:200;not null" json:"report_title"`
	ReportDescription   string    `gorm:"type:text" json:"report_description"`
	DateRange           string    `gorm:"size:100" json:"date_range"`
	TotalSessions       int       `gorm:"default:0" json:"total_sessions"`
	CompletedSessions   int       `gorm:"default:0" json:"completed_sessions"`
	AverageConfidence   float64   `gorm:"type:decimal(5,2);default:0" json:"average_confidence"`
	DominantEmotion     string    `gorm:"size:50" json:"dominant_emotion"`
	EmotionTrend        string    `gorm:"size:20" json:"emotion_trend"` 
	CurrentTherapistID  uint      `gorm:"not null" json:"current_therapist_id"`
	TherapistChanged    bool      `gorm:"default:false" json:"therapist_changed"`
	PreviousTherapistID *uint     `gorm:"index" json:"previous_therapist_id,omitempty"`
	FilePath            string    `gorm:"size:500" json:"file_path"`
	FileFormat          string    `gorm:"size:10;not null" json:"file_format"`
	FileSizeBytes       int64     `gorm:"default:0" json:"file_size_bytes"`
	PDFContent          string    `gorm:"type:longtext" json:"pdf_content,omitempty"`
	CreatedAt           time.Time `gorm:"index" json:"created_at"`
	UpdatedAt           time.Time `json:"updated_at"`
	Paciente            Patient   `gorm:"foreignKey:PacienteID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"paciente,omitempty"`
	GeneratedByUser     Usuarios  `gorm:"foreignKey:GeneratedByUserID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"generated_by_user,omitempty"`
	Session             *TherapySession `gorm:"foreignKey:SessionID;constraint:OnUpdate:CASCADE,OnDelete:SET NULL" json:"session,omitempty"`
	CurrentTherapist    Usuarios  `gorm:"foreignKey:CurrentTherapistID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"current_therapist,omitempty"`
	PreviousTherapist   *Usuarios `gorm:"foreignKey:PreviousTherapistID;constraint:OnUpdate:CASCADE,OnDelete:SET NULL" json:"previous_therapist,omitempty"`
}

func (r *ReportHistory) TableName() string {
	return "report_history"
}


type ReportHistoryResponse struct {
	ID                  uint      `json:"id"`
	PacienteID          uint      `json:"paciente_id"`
	ReportType          string    `json:"report_type"`
	ReportTitle         string    `json:"report_title"`
	ReportDescription   string    `json:"report_description"`
	DateRange           string    `json:"date_range"`
	TotalSessions       int       `json:"total_sessions"`
	CompletedSessions   int       `json:"completed_sessions"`
	AverageConfidence   float64   `json:"average_confidence"`
	DominantEmotion     string    `json:"dominant_emotion"`
	EmotionTrend        string    `json:"emotion_trend"`
	CurrentTherapist    UserBasicInfo `json:"current_therapist"`
	TherapistChanged    bool      `json:"therapist_changed"`
	PreviousTherapist   *UserBasicInfo `json:"previous_therapist,omitempty"`
	FileFormat          string    `json:"file_format"`
	FileSizeBytes       int64     `json:"file_size_bytes"`
	PDFContent          string    `json:"pdf_content,omitempty"`
	CreatedAt           time.Time `json:"created_at"`
	GeneratedByUser     UserBasicInfo `json:"generated_by_user"`
}

type UserBasicInfo struct {
	ID                uint   `json:"id"`
	NombresApellidos  string `json:"nombres_apellidos"`
	Correo           string `json:"correo"`
	Telefono         string `json:"telefono"`
}


type PatientReportSummary struct {
	Patient             PatientDetailInfo        `json:"patient"`
	Caregiver          UserBasicInfo            `json:"caregiver"`
	Sessions           []SessionWithAnalysis    `json:"sessions"`
	EmotionSummary     EmotionAnalysisSummary   `json:"emotion_summary"`
	TherapistHistory   []TherapistAssignment    `json:"therapist_history"`
	GeneratedAt        time.Time                `json:"generated_at"`
	GeneratedBy        UserBasicInfo            `json:"generated_by"`
	ReportPeriod       string                   `json:"report_period"`
}


type PatientDetailInfo struct {
	ID                  uint      `json:"id"`
	SerialID           string    `json:"serial_id"`
	NombresApellidos   string    `json:"nombres_apellidos"`
	FechaNacimiento    time.Time `json:"fecha_nacimiento"`
	Edad               int       `json:"edad"`
	Genero             string    `json:"genero"`
	TipoDocumento      string    `json:"tipo_documento"`
	NumDocumento       string    `json:"num_documento"`
	Telefono           string    `json:"telefono"`
	Direccion          string    `json:"direccion"`
	CondicionMedica    string    `json:"condicion_medica"`
	Medicamentos       []string  `json:"medicamentos"`
	Alergias           []string  `json:"alergias"`
	ContactoEmergencia string    `json:"contacto_emergencia"`
	CreatedAt          time.Time `json:"created_at"`
}


type SessionWithAnalysis struct {
	Session           TherapySessionDetail `json:"session"`
	EmotionAnalysis   *EmotionResult       `json:"emotion_analysis,omitempty"`
	SessionRating     *float64             `json:"session_rating,omitempty"`
	TherapistNotes    string               `json:"therapist_notes"`
}


type TherapySessionDetail struct {
	ID                    uint      `json:"id"`
	FechaSesion          time.Time `json:"fecha_sesion"`
	HoraInicio           string    `json:"hora_inicio"`
	HoraFin              string    `json:"hora_fin"`
	Duracion             int       `json:"duracion"`
	Ubicacion            string    `json:"ubicacion"`
	Direccion            string    `json:"direccion"`
	Descripcion          string    `json:"descripcion"`
	Objetivos            []string  `json:"objetivos"`
	Materiales           []string  `json:"materiales"`
	Estado               string    `json:"estado"`
	TipoSesion           string    `json:"tipo_sesion"`
	Modalidad            string    `json:"modalidad"`
	Terapeuta            UserBasicInfo `json:"terapeuta"`
	TerapeutaReasignado  *UserBasicInfo `json:"terapeuta_reasignado,omitempty"`
}


type EmotionResult struct {
	ID                    uint      `json:"id"`
	SessionID             uint      `json:"session_id"`
	EmotionType           string    `json:"emotion_type"`
	ConfidencePercentage  float64   `json:"confidence_percentage"`
	ImageAnalyzed         bool      `json:"image_analyzed"`
	AnalysisDate          time.Time `json:"analysis_date"`
	Notes                 string    `json:"notes"`
}


type EmotionAnalysisSummary struct {
	TotalAnalyses         int                    `json:"total_analyses"`
	AverageConfidence     float64                `json:"average_confidence"`
	DominantEmotion       string                 `json:"dominant_emotion"`
	EmotionDistribution   map[string]int         `json:"emotion_distribution"`
	ConfidenceTrend       string                 `json:"confidence_trend"`
	EmotionTrend          string                 `json:"emotion_trend"`
	HighestConfidence     float64                `json:"highest_confidence"`
	LowestConfidence      float64                `json:"lowest_confidence"`
	RecentEmotions        []EmotionResult        `json:"recent_emotions"`
}


type TherapistAssignment struct {
	TherapistID       uint      `json:"therapist_id"`
	Therapist         UserBasicInfo `json:"therapist"`
	AssignedDate      time.Time `json:"assigned_date"`
	EndDate           *time.Time `json:"end_date,omitempty"`
	ReasonForChange   string    `json:"reason_for_change"`
	SessionsCompleted int       `json:"sessions_completed"`
	AverageRating     *float64  `json:"average_rating,omitempty"`
}