package dto

import (
	"time"
	"usuarios/models"
)

type CreateReportDTO struct {
	Type            models.ReportType   `json:"type" binding:"required,oneof=session general patient therapist"`
	Format          models.ReportFormat `json:"format" binding:"required,oneof=pdf excel json html"`
	TherapistID     *uint              `json:"therapist_id,omitempty"`
	PatientID       *uint              `json:"patient_id,omitempty"`
	SessionID       *uint              `json:"session_id,omitempty"`
	Title           string             `json:"title" binding:"required,min=3,max=255"`
	Description     string             `json:"description,omitempty"`
	StartDate       *string            `json:"start_date,omitempty"`
	EndDate         *string            `json:"end_date,omitempty"`
	IncludeAnalysis bool              `json:"include_analysis"`
	IncludeCharts   bool              `json:"include_charts"`
	Metadata        map[string]interface{} `json:"metadata,omitempty"`
}

type UpdateReportDTO struct {
	Title           *string            `json:"title,omitempty" binding:"omitempty,min=3,max=255"`
	Description     *string            `json:"description,omitempty"`
	Conclusions     *string            `json:"conclusions,omitempty"`
	Recommendations *string            `json:"recommendations,omitempty"`
	Status          *models.ReportStatus `json:"status,omitempty" binding:"omitempty,oneof=pending processing completed failed"`
	Metadata        map[string]interface{} `json:"metadata,omitempty"`
}

type ReportFilterDTO struct {
	Type        *models.ReportType   `form:"type" binding:"omitempty,oneof=session general patient therapist"`
	Status      *models.ReportStatus `form:"status" binding:"omitempty,oneof=pending processing completed failed"`
	Format      *models.ReportFormat `form:"format" binding:"omitempty,oneof=pdf excel json html"`
	GeneratedBy *uint               `form:"generated_by"`
	TherapistID *uint               `form:"therapist_id"`
	PatientID   *uint               `form:"patient_id"`
	SessionID   *uint               `form:"session_id"`
	StartDate   *string             `form:"start_date"`
	EndDate     *string             `form:"end_date"`
	Search      *string             `form:"search"`
	Page        int                 `form:"page,default=1" binding:"min=1"`
	Limit       int                 `form:"limit,default=5" binding:"min=1,max=100"`
	OrderBy     string              `form:"order_by,default=created_at"`
	Order       string              `form:"order,default=desc" binding:"oneof=asc desc"`
}

type ReportResponse struct {
	ID              uint                   `json:"id"`
	Type            string                 `json:"type"`
	Format          string                 `json:"format"`
	Status          string                 `json:"status"`
	Title           string                 `json:"title"`
	Description     string                 `json:"description,omitempty"`
	GeneratedBy     uint                   `json:"generated_by"`
	GeneratedByUser *UserBasicInfo        `json:"generated_by_user,omitempty"`
	TherapistID     *uint                 `json:"therapist_id,omitempty"`
	Therapist       *UserBasicInfo        `json:"therapist,omitempty"`
	PatientID       *uint                 `json:"patient_id,omitempty"`
	Patient         *struct {
		ID              uint   `json:"id"`
		NombresApellidos string `json:"nombres_apellidos"`
		SerialID        string `json:"serial_id"`
	}                                  `json:"patient,omitempty"`
	SessionID       *uint                 `json:"session_id,omitempty"`
	StartDate       *time.Time            `json:"start_date,omitempty"`
	EndDate         *time.Time            `json:"end_date,omitempty"`
	FilePath        string                `json:"file_path,omitempty"`
	FileSize        int64                 `json:"file_size,omitempty"`
	DownloadURL     string                `json:"download_url,omitempty"`
	TotalSessions   int                   `json:"total_sessions,omitempty"`
	CompletedSessions int                 `json:"completed_sessions,omitempty"`
	CancelledSessions int                 `json:"cancelled_sessions,omitempty"`
	AverageDuration int                   `json:"average_duration,omitempty"`
	Conclusions     string                `json:"conclusions,omitempty"`
	Recommendations string                `json:"recommendations,omitempty"`
	Metadata        map[string]interface{} `json:"metadata,omitempty"`
	ProcessedAt     *time.Time            `json:"processed_at,omitempty"`
	CreatedAt       time.Time             `json:"created_at"`
	UpdatedAt       time.Time             `json:"updated_at"`
}


type PaginatedReportsResponse struct {
	Reports     []ReportResponse `json:"reports"`
	Total       int64           `json:"total"`
	Page        int             `json:"page"`
	Limit       int             `json:"limit"`
	TotalPages  int             `json:"total_pages"`
	HasNext     bool            `json:"has_next"`
	HasPrevious bool            `json:"has_previous"`
}

type ReportStatisticsResponse struct {
	TotalReports      int64                    `json:"total_reports"`
	ReportsByType     map[string]int64        `json:"reports_by_type"`
	ReportsByStatus   map[string]int64        `json:"reports_by_status"`
	AverageGenTime    float64                 `json:"average_gen_time_seconds"`
	MostActiveUsers   []UserReportStatsDTO    `json:"most_active_users"`
	LastGeneratedAt   *time.Time              `json:"last_generated_at,omitempty"`
}

type UserReportStatsDTO struct {
	UserID      uint   `json:"user_id"`
	UserName    string `json:"user_name"`
	ReportCount int64  `json:"report_count"`
}

type GenerateReportRequest struct {
	Type            models.ReportType      `json:"type" binding:"required"`
	Format          models.ReportFormat    `json:"format" binding:"required"`
	Parameters      ReportParameters       `json:"parameters"`
}

type ReportParameters struct {
	SessionID       *uint      `json:"session_id,omitempty"`
	PatientID       *uint      `json:"patient_id,omitempty"`
	TherapistID     *uint      `json:"therapist_id,omitempty"`
	StartDate       *time.Time `json:"start_date,omitempty"`
	EndDate         *time.Time `json:"end_date,omitempty"`
	IncludeAnalysis bool       `json:"include_analysis"`
	IncludeCharts   bool       `json:"include_charts"`
}