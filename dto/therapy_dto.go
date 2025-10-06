package dto

type CreateTherapySessionDTO struct {
	PacienteID     uint     `json:"paciente_id" binding:"required"`
	TerapeutaID    *uint    `json:"terapeuta_id,omitempty"` // Opcional, si no se especifica, se asigna el terapeuta actual
	FechaSesion    string   `json:"fecha_sesion" binding:"required"`
	HoraInicio     string   `json:"hora_inicio" binding:"required"`
	HoraFin        string   `json:"hora_fin" binding:"required"`
	Ubicacion      string   `json:"ubicacion" binding:"required,min=5,max=200"`
	Direccion      string   `json:"direccion" binding:"required,min=10"`
	Descripcion    string   `json:"descripcion" binding:"required,min=20,max=500"`
	Objetivos      []string `json:"objetivos" binding:"required,min=1,max=6"`
	Materiales     []string `json:"materiales" binding:"required,min=1,max=10"`
	TipoSesion     string   `json:"tipo_sesion" binding:"required,oneof=individual grupal familiar"`
	Modalidad      string   `json:"modalidad" binding:"required,oneof=presencial virtual"`
}

type UpdateTherapySessionDTO struct {
	FechaSesion    string   `json:"fecha_sesion,omitempty"`
	HoraInicio     string   `json:"hora_inicio,omitempty"`
	HoraFin        string   `json:"hora_fin,omitempty"`
	Ubicacion      string   `json:"ubicacion,omitempty"`
	Direccion      string   `json:"direccion,omitempty"`
	Descripcion    string   `json:"descripcion,omitempty"`
	Objetivos      []string `json:"objetivos,omitempty"`
	Materiales     []string `json:"materiales,omitempty"`
	NotasTerapeuta string   `json:"notas_terapeuta,omitempty"`
	Estado         string   `json:"estado,omitempty"`
	TipoSesion     string   `json:"tipo_sesion,omitempty"`
	Modalidad      string   `json:"modalidad,omitempty"`
}

type RescheduleSessionDTO struct {
	FechaSesion string `json:"fecha_sesion" binding:"required"`
	HoraInicio  string `json:"hora_inicio" binding:"required"`
	HoraFin     string `json:"hora_fin" binding:"required"`
	Motivo      string `json:"motivo" binding:"required,min=10,max=200"`
}

type SearchSessionsDTO struct {
	Query    string `form:"q"`
	Estado   string `form:"estado"`
	Page     int    `form:"page,default=1"`
	Limit    int    `form:"limit,default=5"`
	PatientID uint  `form:"patient_id"`
}

type SessionResponse struct {
	ID              uint     `json:"id"`
	PacienteID      uint     `json:"paciente_id"`
	TerapeutaID     uint     `json:"terapeuta_id"`
	FechaSesion     string   `json:"fecha_sesion"`
	HoraInicio      string   `json:"hora_inicio"`
	HoraFin         string   `json:"hora_fin"`
	Duracion        int      `json:"duracion"`
	Ubicacion       string   `json:"ubicacion"`
	Direccion       string   `json:"direccion"`
	Descripcion     string   `json:"descripcion"`
	Objetivos       []string `json:"objetivos"`
	Materiales      []string `json:"materiales"`
	NotasTerapeuta  string   `json:"notas_terapeuta"`
	Estado          string   `json:"estado"`
	TipoSesion      string   `json:"tipo_sesion"`
	Modalidad       string   `json:"modalidad"`
	UpdateCount     int      `json:"update_count"`
	CreatedAt       string   `json:"created_at"`
	UpdatedAt       string   `json:"updated_at"`
	Paciente        PatientBasicInfo `json:"paciente"`
	Terapeuta       UserBasicInfo    `json:"terapeuta"`
	Cuidador        *UserBasicInfo   `json:"cuidador,omitempty"`
}

type PatientBasicInfo struct {
	ID               uint   `json:"id"`
	NombresApellidos string `json:"nombres_apellidos"`
	FotoMovil        string `json:"foto_movil"`
}

type UserBasicInfo struct {
	ID                uint   `json:"id"`
	NombresApellidos  string `json:"nombres_apellidos"`
	Correo            string `json:"correo"`
	Telefono          string `json:"telefono"`
}

type PaginatedSessionsResponse struct {
	Sessions    []SessionResponse `json:"sessions"`
	Total       int64             `json:"total"`
	Page        int               `json:"page"`
	Limit       int               `json:"limit"`
	TotalPages  int               `json:"total_pages"`
	HasNext     bool              `json:"has_next"`
	HasPrevious bool              `json:"has_previous"`
}

type PatientStatsResponse struct {
	PatientID          uint   `json:"patient_id"`
	PatientName        string `json:"patient_name"`
	TotalSessions      int    `json:"total_sessions"`
	CompletedSessions  int    `json:"completed_sessions"`
	ScheduledSessions  int    `json:"scheduled_sessions"`
	CancelledSessions  int    `json:"cancelled_sessions"`
	ProgressPercentage int    `json:"progress_percentage"`
	LastSessionDate    string `json:"last_session_date,omitempty"`
	DaysSinceLastSession int  `json:"days_since_last_session"`
	AverageSessionDuration int `json:"average_session_duration"`
}

// ============== THERAPIST RATING DTOs ==============

type CreateTherapistRatingDTO struct {
	SessionID    uint   `json:"session_id" binding:"required"`
	TherapistID  uint   `json:"therapist_id" binding:"required"`
	PatientID    uint   `json:"patient_id" binding:"required"`
	CaregiverID  uint   `json:"caregiver_id" binding:"required"`
	Rating       int    `json:"rating" binding:"required,min=1,max=5"`
	Comment      string `json:"comment,omitempty"`
}

type TherapistRatingResponse struct {
	ID           uint   `json:"id"`
	SessionID    uint   `json:"session_id"`
	TherapistID  uint   `json:"therapist_id"`
	CaregiverID  uint   `json:"caregiver_id"`
	PatientID    uint   `json:"patient_id"`
	Rating       int    `json:"rating"`
	Comment      string `json:"comment"`
	CreatedAt    string `json:"created_at"`
	UpdatedAt    string `json:"updated_at"`

	// Basic info for relationships
	TherapistName string `json:"therapist_name,omitempty"`
	CaregiverName string `json:"caregiver_name,omitempty"`
	PatientName   string `json:"patient_name,omitempty"`
}

type TherapistDisqualificationResponse struct {
	ID           uint   `json:"id"`
	TherapistID  uint   `json:"therapist_id"`
	BadRatings   int    `json:"bad_ratings"`
	IsDeleted    bool   `json:"is_deleted"`
	CreatedAt    string `json:"created_at"`
	UpdatedAt    string `json:"updated_at"`

	TherapistName string `json:"therapist_name"`
}