package models

import (
	"time"
	"gorm.io/gorm"
	"database/sql/driver"
	"encoding/json"
)

type ReportType string
type ReportFormat string
type ReportStatus string

const (
	ReportTypeSession     ReportType = "session"
	ReportTypeGeneral     ReportType = "general"
	ReportTypePatient     ReportType = "patient"
	ReportTypeTherapist   ReportType = "therapist"

	ReportFormatPDF       ReportFormat = "pdf"
	ReportFormatExcel     ReportFormat = "excel"
	ReportFormatJSON      ReportFormat = "json"
	ReportFormatHTML      ReportFormat = "html"

	ReportStatusPending   ReportStatus = "pending"
	ReportStatusProcessing ReportStatus = "processing"
	ReportStatusCompleted ReportStatus = "completed"
	ReportStatusFailed    ReportStatus = "failed"
)

type ReportMetadata map[string]interface{}

func (m ReportMetadata) Value() (driver.Value, error) {
	if m == nil {
		return nil, nil
	}
	return json.Marshal(m)
}

func (m *ReportMetadata) Scan(value interface{}) error {
	if value == nil {
		*m = nil
		return nil
	}

	var data []byte
	switch v := value.(type) {
	case []byte:
		data = v
	case string:
		data = []byte(v)
	default:
		data = []byte("{}")
	}

	return json.Unmarshal(data, m)
}

type Report struct {
	ID              uint           `gorm:"primaryKey;autoIncrement" json:"id"`
	Type            ReportType     `gorm:"type:varchar(20);not null;index" json:"type"`
	Format          ReportFormat   `gorm:"type:varchar(10);not null" json:"format"`
	Status          ReportStatus   `gorm:"type:varchar(20);not null;default:'pending';index" json:"status"`

	GeneratedBy     uint           `gorm:"not null;index" json:"generated_by"`
	TherapistID     *uint          `gorm:"index" json:"therapist_id,omitempty"`
	PatientID       *uint          `gorm:"index" json:"patient_id,omitempty"`
	SessionID       *uint          `gorm:"index" json:"session_id,omitempty"`

	Title           string         `gorm:"type:varchar(255);not null" json:"title"`
	Description     string         `gorm:"type:text" json:"description"`

	StartDate       *time.Time     `gorm:"index" json:"start_date,omitempty"`
	EndDate         *time.Time     `gorm:"index" json:"end_date,omitempty"`

	FilePath        string         `gorm:"type:varchar(500)" json:"file_path,omitempty"`
	FileSize        int64          `json:"file_size,omitempty"`

	Metadata        ReportMetadata `gorm:"type:json" json:"metadata,omitempty"`

	TotalSessions   int            `json:"total_sessions,omitempty"`
	CompletedSessions int          `json:"completed_sessions,omitempty"`
	CancelledSessions int          `json:"cancelled_sessions,omitempty"`
	AverageDuration int            `json:"average_duration,omitempty"`

	Conclusions     string         `gorm:"type:text" json:"conclusions,omitempty"`
	Recommendations string         `gorm:"type:text" json:"recommendations,omitempty"`

	ProcessingError string         `gorm:"type:text" json:"processing_error,omitempty"`
	ProcessedAt     *time.Time     `json:"processed_at,omitempty"`

	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
	DeletedAt       gorm.DeletedAt `gorm:"index" json:"deleted_at,omitempty"`

	GeneratedByUser *Usuarios      `gorm:"foreignKey:GeneratedBy" json:"generated_by_user,omitempty"`
	Therapist       *Usuarios      `gorm:"foreignKey:TherapistID" json:"therapist,omitempty"`
	Patient         *Patient       `gorm:"foreignKey:PatientID" json:"patient,omitempty"`
	Session         *TherapySession `gorm:"foreignKey:SessionID" json:"session,omitempty"`
}

func (Report) TableName() string {
	return "reports"
}

type ReportStatistics struct {
	TotalReports      int64              `json:"total_reports"`
	ReportsByType     map[string]int64   `json:"reports_by_type"`
	ReportsByStatus   map[string]int64   `json:"reports_by_status"`
	AverageGenTime    float64            `json:"average_gen_time_seconds"`
	MostActiveUsers   []UserReportStats  `json:"most_active_users"`
}

type UserReportStats struct {
	UserID      uint   `json:"user_id"`
	UserName    string `json:"user_name"`
	ReportCount int64  `json:"report_count"`
}