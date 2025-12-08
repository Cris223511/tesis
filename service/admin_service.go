package services

import (
	"log"
	"time"
	"usuarios/models"
	"gorm.io/gorm"
	 "database/sql"
)




type AdminService struct {
	DB *gorm.DB
}

func NewAdminService(db *gorm.DB) *AdminService {
	return &AdminService{DB: db}
}

type AdminStats struct {
	TotalPatients     int     `json:"total_patients"`
	TotalTherapists   int     `json:"total_therapists"`
	TotalSessions     int     `json:"total_sessions"`
	CompletedSessions int     `json:"completed_sessions"`
	AverageRating     float64 `json:"average_rating"`
	CompletionRate    int     `json:"completion_rate"`
}

func (s *AdminService) GetAdminStats() (*AdminStats, error) {
	stats := &AdminStats{}

	var activePatients int64
	if err := s.DB.Model(&models.Patient{}).
		Where("activo = ?", 0).
		Count(&activePatients).Error; err != nil {
		return nil, err
	}
	stats.TotalPatients = int(activePatients)

	var totalTherapists int64
	if err := s.DB.Table("usuarios").
		Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
		Where("user_roles.roles_id = ? AND usuarios.activo = ?", 4, 0).
		Distinct("usuarios.idusuario").
		Count(&totalTherapists).Error; err != nil {
		log.Printf("[ADMIN_STATS] Error counting therapists: %v", err)
		return nil, err
	}
	log.Printf("[ADMIN_STATS] Total therapists found: %d", totalTherapists)
	stats.TotalTherapists = int(totalTherapists)

	var totalSessions int64
	if err := s.DB.Model(&models.TherapySession{}).
		Where("is_deleted = ?", false).
		Count(&totalSessions).Error; err != nil {
		return nil, err
	}
	stats.TotalSessions = int(totalSessions)

	var completedSessions int64
	if err := s.DB.Model(&models.TherapySession{}).
		Where("estado = ? AND is_deleted = ?", "completada", false).
		Count(&completedSessions).Error; err != nil {
		return nil, err
	}
	stats.CompletedSessions = int(completedSessions)

	if stats.TotalSessions > 0 {
		stats.CompletionRate = int((float64(stats.CompletedSessions) / float64(stats.TotalSessions)) * 100)
	} else {
		stats.CompletionRate = 0
	}

	var avgRating sql.NullFloat64
	if err := s.DB.Model(&models.TherapistRating{}).
		Select("AVG(rating)").
		Scan(&avgRating).Error; err != nil {
		return nil, err
	}

	if avgRating.Valid {
		stats.AverageRating = avgRating.Float64
	} else {
		stats.AverageRating = 0.0
	}

	return stats, nil
}

type UserFilter struct {
	Role   string
	Activo *bool
	Page   int
	Limit  int
}

func (s *AdminService) GetUsersByRole(filter UserFilter) ([]models.Usuarios, int64, error) {
	var users []models.Usuarios
	var total int64

	query := s.DB.Model(&models.Usuarios{})

	if filter.Role != "" {
		query = query.Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
			Joins("JOIN roles ON user_roles.roles_id = roles.id").
			Where("roles.name = ?", filter.Role)
	}

	if filter.Activo != nil {
		query = query.Where("usuarios.activo = ?", *filter.Activo)
	}

	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (filter.Page - 1) * filter.Limit
	if err := query.Offset(offset).Limit(filter.Limit).
		Preload("Roles").
		Find(&users).Error; err != nil {
		return nil, 0, err
	}

	return users, total, nil
}

type SessionStats struct {
	TotalSessions     int            `json:"total_sessions"`
	ByStatus          map[string]int `json:"by_status"`
	ByTherapist       map[int]int    `json:"by_therapist"`
	ThisMonth         int            `json:"this_month"`
	LastMonth         int            `json:"last_month"`
	CompletionRate    float64        `json:"completion_rate"`
}

func (s *AdminService) GetSessionStats(dateFrom, dateTo *time.Time) (*SessionStats, error) {
	stats := &SessionStats{
		ByStatus:    make(map[string]int),
		ByTherapist: make(map[int]int),
	}

	query := s.DB.Model(&models.TherapySession{}).Where("is_deleted = ?", false)

	if dateFrom != nil {
		query = query.Where("fecha_sesion >= ?", dateFrom)
	}
	if dateTo != nil {
		query = query.Where("fecha_sesion <= ?", dateTo)
	}

	var total int64
	if err := query.Count(&total).Error; err != nil {
		return nil, err
	}
	stats.TotalSessions = int(total)

	var sessions []models.TherapySession
	if err := query.Find(&sessions).Error; err != nil {
		return nil, err
	}

	for _, session := range sessions {
		stats.ByStatus[session.Estado]++
		if session.TerapeutaID != 0 {
			therapistID := session.TerapeutaID
			stats.ByTherapist[int(therapistID)]++
		}
	}

	now := time.Now()
	startOfMonth := time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, now.Location())
	startOfLastMonth := startOfMonth.AddDate(0, -1, 0)

	var thisMonthCount, lastMonthCount int64

	s.DB.Model(&models.TherapySession{}).
		Where("fecha_sesion >= ? AND fecha_sesion < ? AND is_deleted = ?", startOfMonth, now, false).
		Count(&thisMonthCount)
	stats.ThisMonth = int(thisMonthCount)

	s.DB.Model(&models.TherapySession{}).
		Where("fecha_sesion >= ? AND fecha_sesion < ? AND is_deleted = ?", startOfLastMonth, startOfMonth, false).
		Count(&lastMonthCount)
	stats.LastMonth = int(lastMonthCount)

	if stats.TotalSessions > 0 {
		completedCount := stats.ByStatus["completada"]
		stats.CompletionRate = (float64(completedCount) / float64(stats.TotalSessions)) * 100
	}

	return stats, nil
}

