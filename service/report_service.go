package services

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"
	"time"
	"usuarios/dto"
	"usuarios/models"

	"github.com/jung-kurt/gofpdf"
	"gorm.io/gorm"
)

type ReportService struct {
	db               *gorm.DB
	emotionMLService *EmotionMLService
}

func NewReportService(db *gorm.DB, emotionMLService *EmotionMLService) *ReportService {
	return &ReportService{db: db, emotionMLService: emotionMLService}
}

type sessionEmotionSummary struct {
	SessionID       uint    `json:"session_id"`
	Emotion         string  `json:"emotion"`
	ConfidenceScore float64 `json:"confidence_score"`
	ConfidenceText  string  `json:"confidence_text"`
	RecordedAt      string  `json:"recorded_at,omitempty"`
}

type patientEmotionSummary struct {
	PatientID       uint    `json:"patient_id"`
	PatientName     string  `json:"patient_name"`
	Emotion         string  `json:"emotion"`
	ConfidenceScore float64 `json:"confidence_score"`
	ConfidenceText  string  `json:"confidence_text"`
	SessionID       uint    `json:"session_id"`
}

func (s *ReportService) Create(createDTO *dto.CreateReportDTO, userID uint) (*models.Report, error) {
	report := &models.Report{
		Type:        createDTO.Type,
		Format:      createDTO.Format,
		Status:      models.ReportStatusPending,
		GeneratedBy: userID,
		TherapistID: createDTO.TherapistID,
		PatientID:   createDTO.PatientID,
		SessionID:   createDTO.SessionID,
		Title:       createDTO.Title,
		Description: createDTO.Description,
		Metadata:    models.ReportMetadata(createDTO.Metadata),
	}

	if createDTO.StartDate != nil {
		startDate, err := time.Parse("2006-01-02", *createDTO.StartDate)
		if err != nil {
			return nil, errors.New("formato de fecha inicio inválido")
		}
		report.StartDate = &startDate
	}

	if createDTO.EndDate != nil {
		endDate, err := time.Parse("2006-01-02", *createDTO.EndDate)
		if err != nil {
			return nil, errors.New("formato de fecha fin inválido")
		}
		report.EndDate = &endDate
	}

	if err := s.db.Create(report).Error; err != nil {
		return nil, err
	}

	go s.processReport(report.ID, createDTO)

	return report, nil
}

func (s *ReportService) processReport(reportID uint, createDTO *dto.CreateReportDTO) {
	var report models.Report
	if err := s.db.First(&report, reportID).Error; err != nil {
		s.updateReportStatus(reportID, models.ReportStatusFailed, err.Error())
		return
	}

	s.updateReportStatus(reportID, models.ReportStatusProcessing, "")

	switch report.Type {
	case models.ReportTypeSession:
		s.generateSessionReport(&report, createDTO)
	case models.ReportTypeGeneral:
		s.generateGeneralReport(&report, createDTO)
	case models.ReportTypePatient:
		s.generatePatientReport(&report, createDTO)
	case models.ReportTypeTherapist:
		s.generateTherapistReport(&report, createDTO)
	default:
		s.updateReportStatus(reportID, models.ReportStatusFailed, "tipo de reporte no soportado")
		return
	}
}

func (s *ReportService) generateSessionReport(report *models.Report, createDTO *dto.CreateReportDTO) {
	if report.SessionID == nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "ID de sesión requerido")
		return
	}

	var session models.TherapySession
	if err := s.db.Preload("Paciente").Preload("Terapeuta").First(&session, *report.SessionID).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "sesión no encontrada")
		return
	}

	report.Metadata = s.buildSessionReportMetadata(&session)
	stats := s.calculateSessionStats(&session)

	report.TotalSessions = 1
	report.CompletedSessions = 0
	if session.Estado == "completada" {
		report.CompletedSessions = 1
	}
	report.AverageDuration = session.Duracion
	report.Conclusions = s.generateSessionConclusions(&session, stats)
	report.Recommendations = s.generateSessionRecommendations(&session, stats)

	filePath, err := s.generateReportFile(report, &session, nil, createDTO)
	if err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, err.Error())
		return
	}

	fileInfo, _ := os.Stat(filePath)
	now := time.Now()

	s.db.Model(&models.Report{}).Where("id = ?", report.ID).Updates(map[string]interface{}{
		"status":             models.ReportStatusCompleted,
		"file_path":          filePath,
		"file_size":          fileInfo.Size(),
		"metadata":           report.Metadata,
		"total_sessions":     report.TotalSessions,
		"completed_sessions": report.CompletedSessions,
		"average_duration":   report.AverageDuration,
		"conclusions":        report.Conclusions,
		"recommendations":    report.Recommendations,
		"processed_at":       now,
	})
}

func (s *ReportService) generateGeneralReport(report *models.Report, createDTO *dto.CreateReportDTO) {
	query := s.db.Model(&models.TherapySession{})

	if report.TherapistID != nil {
		query = query.Where("terapeuta_id = ?", *report.TherapistID)
	}

	if report.StartDate != nil && report.EndDate != nil {
		query = query.Where("fecha_sesion BETWEEN ? AND ?", *report.StartDate, *report.EndDate)
	}

	var sessions []models.TherapySession
	if err := query.Preload("Paciente").Preload("Terapeuta").Find(&sessions).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "error obteniendo sesiones")
		return
	}

	report.Metadata = s.buildGeneralReportMetadata(sessions)
	stats := s.calculateGeneralStats(sessions)

	report.TotalSessions = stats.Total
	report.CompletedSessions = stats.Completed
	report.CancelledSessions = stats.Cancelled
	report.AverageDuration = stats.AverageDuration
	report.Conclusions = s.generateGeneralConclusions(sessions, stats)
	report.Recommendations = s.generateGeneralRecommendations(stats)

	filePath, err := s.generateReportFile(report, nil, sessions, createDTO)
	if err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, err.Error())
		return
	}

	fileInfo, _ := os.Stat(filePath)
	now := time.Now()

	s.db.Model(&models.Report{}).Where("id = ?", report.ID).Updates(map[string]interface{}{
		"status":             models.ReportStatusCompleted,
		"file_path":          filePath,
		"file_size":          fileInfo.Size(),
		"metadata":           report.Metadata,
		"total_sessions":     report.TotalSessions,
		"completed_sessions": report.CompletedSessions,
		"cancelled_sessions": report.CancelledSessions,
		"average_duration":   report.AverageDuration,
		"conclusions":        report.Conclusions,
		"recommendations":    report.Recommendations,
		"processed_at":       now,
	})
}

func (s *ReportService) generatePatientReport(report *models.Report, createDTO *dto.CreateReportDTO) {
	if report.PatientID == nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "ID de paciente requerido")
		return
	}

	var patient models.Patient
	if err := s.db.First(&patient, *report.PatientID).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "paciente no encontrado")
		return
	}

	var sessions []models.TherapySession
	query := s.db.Where("paciente_id = ?", *report.PatientID)

	if report.StartDate != nil && report.EndDate != nil {
		query = query.Where("fecha_sesion BETWEEN ? AND ?", *report.StartDate, *report.EndDate)
	}

	if err := query.Preload("Terapeuta").Find(&sessions).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "error obteniendo sesiones del paciente")
		return
	}

	stats := s.calculateGeneralStats(sessions)
	report.TotalSessions = stats.Total
	report.CompletedSessions = stats.Completed
	report.CancelledSessions = stats.Cancelled
	report.AverageDuration = stats.AverageDuration
	report.Conclusions = s.generatePatientConclusions(&patient, sessions, stats)
	report.Recommendations = s.generatePatientRecommendations(&patient, stats)

	filePath, err := s.generateReportFile(report, nil, sessions, createDTO)
	if err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, err.Error())
		return
	}

	fileInfo, _ := os.Stat(filePath)
	now := time.Now()

	s.db.Model(&models.Report{}).Where("id = ?", report.ID).Updates(map[string]interface{}{
		"status":             models.ReportStatusCompleted,
		"file_path":          filePath,
		"file_size":          fileInfo.Size(),
		"total_sessions":     report.TotalSessions,
		"completed_sessions": report.CompletedSessions,
		"cancelled_sessions": report.CancelledSessions,
		"average_duration":   report.AverageDuration,
		"conclusions":        report.Conclusions,
		"recommendations":    report.Recommendations,
		"processed_at":       now,
	})
}

func (s *ReportService) generateTherapistReport(report *models.Report, createDTO *dto.CreateReportDTO) {
	if report.TherapistID == nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "ID de terapeuta requerido")
		return
	}

	var therapist models.Usuarios
	if err := s.db.First(&therapist, *report.TherapistID).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "terapeuta no encontrado")
		return
	}

	var sessions []models.TherapySession
	query := s.db.Where("terapeuta_id = ?", *report.TherapistID)

	if report.StartDate != nil && report.EndDate != nil {
		query = query.Where("fecha_sesion BETWEEN ? AND ?", *report.StartDate, *report.EndDate)
	}

	if err := query.Preload("Paciente").Find(&sessions).Error; err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, "error obteniendo sesiones del terapeuta")
		return
	}

	stats := s.calculateGeneralStats(sessions)
	report.TotalSessions = stats.Total
	report.CompletedSessions = stats.Completed
	report.CancelledSessions = stats.Cancelled
	report.AverageDuration = stats.AverageDuration
	report.Conclusions = s.generateTherapistConclusions(&therapist, sessions, stats)
	report.Recommendations = s.generateTherapistRecommendations(&therapist, stats)

	filePath, err := s.generateReportFile(report, nil, sessions, createDTO)
	if err != nil {
		s.updateReportStatus(report.ID, models.ReportStatusFailed, err.Error())
		return
	}

	fileInfo, _ := os.Stat(filePath)
	now := time.Now()

	s.db.Model(&models.Report{}).Where("id = ?", report.ID).Updates(map[string]interface{}{
		"status":             models.ReportStatusCompleted,
		"file_path":          filePath,
		"file_size":          fileInfo.Size(),
		"total_sessions":     report.TotalSessions,
		"completed_sessions": report.CompletedSessions,
		"cancelled_sessions": report.CancelledSessions,
		"average_duration":   report.AverageDuration,
		"conclusions":        report.Conclusions,
		"recommendations":    report.Recommendations,
		"processed_at":       now,
	})
}

func (s *ReportService) Update(id uint, updateDTO *dto.UpdateReportDTO, userID uint) (*models.Report, error) {
	var report models.Report
	if err := s.db.First(&report, id).Error; err != nil {
		return nil, errors.New("reporte no encontrado")
	}

	if report.GeneratedBy != userID && !s.isAdmin(userID) {
		return nil, errors.New("sin permisos para editar este reporte")
	}

	updates := make(map[string]interface{})

	if updateDTO.Title != nil {
		updates["title"] = *updateDTO.Title
	}
	if updateDTO.Description != nil {
		updates["description"] = *updateDTO.Description
	}
	if updateDTO.Conclusions != nil {
		updates["conclusions"] = *updateDTO.Conclusions
	}
	if updateDTO.Recommendations != nil {
		updates["recommendations"] = *updateDTO.Recommendations
	}
	if updateDTO.Status != nil {
		updates["status"] = *updateDTO.Status
	}
	if updateDTO.Metadata != nil {
		updates["metadata"] = models.ReportMetadata(updateDTO.Metadata)
	}

	if err := s.db.Model(&report).Updates(updates).Error; err != nil {
		return nil, err
	}

	return &report, nil
}

func (s *ReportService) Delete(id uint, userID uint) error {
	var report models.Report
	if err := s.db.First(&report, id).Error; err != nil {
		return errors.New("reporte no encontrado")
	}

	if report.GeneratedBy != userID && !s.isAdmin(userID) {
		return errors.New("sin permisos para eliminar este reporte")
	}

	if report.FilePath != "" {
		os.Remove(report.FilePath)
	}

	return s.db.Delete(&report).Error
}

func (s *ReportService) GetByID(id uint) (*models.Report, error) {
	var report models.Report
	err := s.db.Preload("GeneratedByUser").Preload("Therapist").Preload("Patient").Preload("Session").First(&report, id).Error
	if err != nil {
		return nil, errors.New("reporte no encontrado")
	}
	return &report, nil
}

func (s *ReportService) List(filter *dto.ReportFilterDTO) (*dto.PaginatedReportsResponse, error) {
	query := s.db.Model(&models.Report{})

	if filter.Type != nil {
		query = query.Where("type = ?", *filter.Type)
	}
	if filter.Status != nil {
		query = query.Where("status = ?", *filter.Status)
	}
	if filter.Format != nil {
		query = query.Where("format = ?", *filter.Format)
	}
	if filter.GeneratedBy != nil {
		query = query.Where("generated_by = ?", *filter.GeneratedBy)
	}
	if filter.TherapistID != nil {
		query = query.Where("therapist_id = ?", *filter.TherapistID)
	}
	if filter.PatientID != nil {
		query = query.Where("patient_id = ?", *filter.PatientID)
	}
	if filter.SessionID != nil {
		query = query.Where("session_id = ?", *filter.SessionID)
	}

	if filter.StartDate != nil && filter.EndDate != nil {
		startDate, _ := time.Parse("2006-01-02", *filter.StartDate)
		endDate, _ := time.Parse("2006-01-02", *filter.EndDate)
		query = query.Where("created_at BETWEEN ? AND ?", startDate, endDate)
	}

	if filter.Search != nil && *filter.Search != "" {
		searchTerm := "%" + strings.ToLower(*filter.Search) + "%"
		query = query.Where("LOWER(title) LIKE ? OR LOWER(description) LIKE ?", searchTerm, searchTerm)
	}

	var total int64
	query.Count(&total)

	offset := (filter.Page - 1) * filter.Limit
	orderClause := fmt.Sprintf("%s %s", filter.OrderBy, filter.Order)

	var reports []models.Report
	err := query.Preload("GeneratedByUser").Preload("Therapist").Preload("Patient").
		Order(orderClause).Limit(filter.Limit).Offset(offset).Find(&reports).Error

	if err != nil {
		return nil, err
	}

	totalPages := int(math.Ceil(float64(total) / float64(filter.Limit)))

	response := &dto.PaginatedReportsResponse{
		Reports:     s.toReportResponses(reports),
		Total:       total,
		Page:        filter.Page,
		Limit:       filter.Limit,
		TotalPages:  totalPages,
		HasNext:     filter.Page < totalPages,
		HasPrevious: filter.Page > 1,
	}

	return response, nil
}

func (s *ReportService) GetStatistics(userID uint) (*dto.ReportStatisticsResponse, error) {
	var stats dto.ReportStatisticsResponse

	s.db.Model(&models.Report{}).Count(&stats.TotalReports)

	var typeStats []struct {
		Type  string
		Count int64
	}
	s.db.Model(&models.Report{}).Select("type, count(*) as count").Group("type").Scan(&typeStats)
	stats.ReportsByType = make(map[string]int64)
	for _, ts := range typeStats {
		stats.ReportsByType[ts.Type] = ts.Count
	}

	var statusStats []struct {
		Status string
		Count  int64
	}
	s.db.Model(&models.Report{}).Select("status, count(*) as count").Group("status").Scan(&statusStats)
	stats.ReportsByStatus = make(map[string]int64)
	for _, ss := range statusStats {
		stats.ReportsByStatus[ss.Status] = ss.Count
	}

	var avgTime float64
	s.db.Model(&models.Report{}).Where("processed_at IS NOT NULL").
		Select("AVG(TIMESTAMPDIFF(SECOND, created_at, processed_at))").Scan(&avgTime)
	stats.AverageGenTime = avgTime

	var userStats []dto.UserReportStatsDTO
	s.db.Model(&models.Report{}).
		Select("generated_by as user_id, count(*) as report_count").
		Group("generated_by").
		Order("report_count desc").
		Limit(5).
		Scan(&userStats)

	for i := range userStats {
		var user models.Usuarios
		if s.db.First(&user, userStats[i].UserID).Error == nil {
			userStats[i].UserName = user.Nombres_Apellidos
		}
	}
	stats.MostActiveUsers = userStats

	var lastGenerated time.Time
	s.db.Model(&models.Report{}).Select("MAX(created_at)").Scan(&lastGenerated)
	if !lastGenerated.IsZero() {
		stats.LastGeneratedAt = &lastGenerated
	}

	return &stats, nil
}

func (s *ReportService) DownloadReport(id uint) (io.Reader, string, error) {
	var report models.Report
	if err := s.db.First(&report, id).Error; err != nil {
		return nil, "", errors.New("reporte no encontrado")
	}

	if report.Status != models.ReportStatusCompleted {
		return nil, "", errors.New("reporte no está completo")
	}

	file, err := os.Open(report.FilePath)
	if err != nil {
		return nil, "", errors.New("archivo de reporte no encontrado")
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		return nil, "", err
	}

	fileName := filepath.Base(report.FilePath)
	return bytes.NewReader(data), fileName, nil
}

func (s *ReportService) buildSessionReportMetadata(session *models.TherapySession) models.ReportMetadata {
	metadata := models.ReportMetadata{}
	if s.emotionMLService == nil {
		return metadata
	}

	analysis, err := s.emotionMLService.GetSessionAnalysis(session.ID)
	if err != nil || analysis == nil {
		return metadata
	}

	metadata["session_emotion"] = sessionEmotionSummary{
		SessionID:       session.ID,
		Emotion:         s.translateEmotion(analysis.DominantEmotion),
		ConfidenceScore: analysis.ConfidenceScore,
		ConfidenceText:  s.formatConfidence(analysis.ConfidenceScore),
		RecordedAt:      analysis.CreatedAt.Format("02/01/2006 15:04"),
	}
	return metadata
}

func (s *ReportService) buildGeneralReportMetadata(sessions []models.TherapySession) models.ReportMetadata {
	metadata := models.ReportMetadata{}
	if s.emotionMLService == nil || len(sessions) == 0 {
		return metadata
	}

	sessionIDs := make([]uint, 0, len(sessions))
	sessionByID := make(map[uint]models.TherapySession, len(sessions))
	for _, session := range sessions {
		sessionIDs = append(sessionIDs, session.ID)
		sessionByID[session.ID] = session
	}

	analyses, err := s.emotionMLService.GetSessionAnalyses(sessionIDs)
	if err != nil || len(analyses) == 0 {
		return metadata
	}

	sessionEmotionRows := make([]sessionEmotionSummary, 0, len(analyses))
	patientEmotions := make(map[uint]patientEmotionSummary)
	patientEmotionTimes := make(map[uint]time.Time)

	for sessionID, analysis := range analyses {
		sessionData, exists := sessionByID[sessionID]
		if !exists {
			continue
		}

		row := sessionEmotionSummary{
			SessionID:       sessionID,
			Emotion:         s.translateEmotion(analysis.DominantEmotion),
			ConfidenceScore: analysis.ConfidenceScore,
			ConfidenceText:  s.formatConfidence(analysis.ConfidenceScore),
			RecordedAt:      analysis.CreatedAt.Format("02/01/2006 15:04"),
		}
		sessionEmotionRows = append(sessionEmotionRows, row)

		_, hasCurrent := patientEmotions[sessionData.PacienteID]
		if !hasCurrent || analysis.CreatedAt.After(patientEmotionTimes[sessionData.PacienteID]) {
			patientEmotions[sessionData.PacienteID] = patientEmotionSummary{
				PatientID:       sessionData.PacienteID,
				PatientName:     sessionData.Paciente.NombresApellidos,
				Emotion:         row.Emotion,
				ConfidenceScore: row.ConfidenceScore,
				ConfidenceText:  row.ConfidenceText,
				SessionID:       sessionID,
			}
			patientEmotionTimes[sessionData.PacienteID] = analysis.CreatedAt
		}
	}

	patientRows := make([]patientEmotionSummary, 0, len(patientEmotions))
	for _, row := range patientEmotions {
		patientRows = append(patientRows, row)
	}

	metadata["session_emotions"] = sessionEmotionRows
	metadata["patient_emotions"] = patientRows
	return metadata
}

func (s *ReportService) translateEmotion(raw string) string {
	switch strings.ToLower(raw) {
	case "happy":
		return "Felicidad"
	case "sad":
		return "Tristeza"
	case "angry":
		return "Enojo"
	case "surprise":
		return "Sorpresa"
	case "disgust":
		return "Disgusto"
	case "neutral", "fear":
		return "Sin predominio claro"
	default:
		if raw == "" {
			return "No disponible"
		}
		normalized := strings.ToLower(raw)
		return strings.ToUpper(normalized[:1]) + normalized[1:]
	}
}

func (s *ReportService) formatConfidence(score float64) string {
	percent := score
	if percent <= 1 {
		percent *= 100
	}
	return fmt.Sprintf("%.1f%%", percent)
}

func (s *ReportService) generateReportFile(report *models.Report, session *models.TherapySession, sessions []models.TherapySession, createDTO *dto.CreateReportDTO) (string, error) {
	reportsDir := "./reports"
	if err := os.MkdirAll(reportsDir, 0755); err != nil {
		return "", err
	}

	fileName := fmt.Sprintf("report_%d_%s.%s", report.ID, time.Now().Format("20060102_150405"), report.Format)
	filePath := filepath.Join(reportsDir, fileName)

	switch report.Format {
	case models.ReportFormatPDF:
		return filePath, s.generatePDF(filePath, report, session, sessions)
	case models.ReportFormatJSON:
		return filePath, s.generateJSON(filePath, report, session, sessions)
	case models.ReportFormatHTML:
		return filePath, s.generateHTML(filePath, report, session, sessions)
	case models.ReportFormatExcel:
		return filePath, s.generateExcel(filePath, report, session, sessions)
	default:
		return "", errors.New("formato no soportado")
	}
}

func (s *ReportService) generatePDF(filePath string, report *models.Report, session *models.TherapySession, sessions []models.TherapySession) error {
	pdf := gofpdf.New("P", "mm", "A4", "")
	pdf.SetMargins(20, 20, 20)
	pdf.AddPage()

	pdf.SetFont("Arial", "B", 24)
	pdf.SetTextColor(33, 150, 243)
	pdf.CellFormat(0, 15, report.Title, "0", 1, "C", false, 0, "")

	pdf.SetFont("Arial", "", 12)
	pdf.SetTextColor(0, 0, 0)
	pdf.Ln(10)

	if session != nil {
		s.addSessionDetailsToPDF(pdf, session)
		if emotion := s.getSessionEmotionMetadata(report); emotion != nil {
			pdf.Ln(5)
			pdf.SetFont("Arial", "B", 12)
			pdf.CellFormat(0, 8, "Estado Emocional", "0", 1, "", false, 0, "")
			pdf.SetFont("Arial", "", 11)
			pdf.CellFormat(60, 6, "Emoción dominante:", "0", 0, "", false, 0, "")
			pdf.CellFormat(0, 6, emotion.Emotion, "0", 1, "", false, 0, "")
			pdf.CellFormat(60, 6, "Confianza:", "0", 0, "", false, 0, "")
			pdf.CellFormat(0, 6, emotion.ConfidenceText, "0", 1, "", false, 0, "")
		}
	} else if len(sessions) > 0 {
		s.addSessionsListToPDF(pdf, report, sessions)
		patientEmotions := s.getPatientEmotionMetadata(report)
		if len(patientEmotions) > 0 {
			pdf.Ln(8)
			pdf.SetFont("Arial", "B", 12)
			pdf.CellFormat(0, 8, "Estado Emocional por Paciente", "0", 1, "", false, 0, "")
			pdf.SetFont("Arial", "B", 10)
			pdf.CellFormat(70, 7, "Paciente", "1", 0, "C", false, 0, "")
			pdf.CellFormat(50, 7, "Emoción", "1", 0, "C", false, 0, "")
			pdf.CellFormat(50, 7, "Confianza", "1", 1, "C", false, 0, "")
			pdf.SetFont("Arial", "", 9)
			for _, item := range patientEmotions {
				pdf.CellFormat(70, 6, truncateString(item.PatientName, 30), "1", 0, "", false, 0, "")
				pdf.CellFormat(50, 6, item.Emotion, "1", 0, "", false, 0, "")
				pdf.CellFormat(50, 6, item.ConfidenceText, "1", 1, "", false, 0, "")
			}
		}
	}

	if report.Conclusions != "" {
		pdf.Ln(10)
		pdf.SetFont("Arial", "B", 14)
		pdf.CellFormat(0, 10, "Conclusiones", "0", 1, "", false, 0, "")
		pdf.SetFont("Arial", "", 11)
		pdf.MultiCell(0, 6, report.Conclusions, "0", "", false)
	}

	if report.Recommendations != "" {
		pdf.Ln(10)
		pdf.SetFont("Arial", "B", 14)
		pdf.CellFormat(0, 10, "Recomendaciones", "0", 1, "", false, 0, "")
		pdf.SetFont("Arial", "", 11)
		pdf.MultiCell(0, 6, report.Recommendations, "0", "", false)
	}

	pdf.SetY(-20)
	pdf.SetFont("Arial", "I", 10)
	pdf.SetTextColor(128, 128, 128)
	pdf.CellFormat(0, 10, fmt.Sprintf("Generado el %s", time.Now().Format("02/01/2006 15:04")), "0", 0, "C", false, 0, "")

	return pdf.OutputFileAndClose(filePath)
}

func (s *ReportService) generateJSON(filePath string, report *models.Report, session *models.TherapySession, sessions []models.TherapySession) error {
	data := map[string]interface{}{
		"report":       report,
		"session":      session,
		"sessions":     sessions,
		"generated_at": time.Now(),
	}

	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(filePath, jsonData, 0644)
}

func (s *ReportService) generateHTML(filePath string, report *models.Report, session *models.TherapySession, sessions []models.TherapySession) error {
	html := s.buildHTMLReport(report, session, sessions)
	return os.WriteFile(filePath, []byte(html), 0644)
}

func (s *ReportService) generateExcel(filePath string, report *models.Report, session *models.TherapySession, sessions []models.TherapySession) error {
	return errors.New("formato Excel no implementado aún")
}

func (s *ReportService) updateReportStatus(reportID uint, status models.ReportStatus, errorMsg string) {
	updates := map[string]interface{}{"status": status}
	if errorMsg != "" {
		updates["processing_error"] = errorMsg
	}
	if status == models.ReportStatusCompleted {
		now := time.Now()
		updates["processed_at"] = &now
	}
	s.db.Model(&models.Report{}).Where("id = ?", reportID).Updates(updates)
}

func (s *ReportService) isAdmin(userID uint) bool {
	var user models.Usuarios
	if err := s.db.Preload("Roles").First(&user, userID).Error; err != nil {
		return false
	}

	for _, role := range user.Roles {
		if role.Name == "AD" || role.Name == "admin" {
			return true
		}
	}
	return false
}

type SessionStats struct {
	Total           int
	Completed       int
	Cancelled       int
	InProgress      int
	AverageDuration int
	UniquePatients  int
	UniqueTerapists int
}

func (s *ReportService) calculateSessionStats(session *models.TherapySession) SessionStats {
	return SessionStats{
		Total:           1,
		Completed:       boolToInt(session.Estado == "completada"),
		Cancelled:       boolToInt(session.Estado == "cancelada"),
		InProgress:      boolToInt(session.Estado == "en_progreso"),
		AverageDuration: session.Duracion,
		UniquePatients:  1,
		UniqueTerapists: 1,
	}
}

func (s *ReportService) calculateGeneralStats(sessions []models.TherapySession) SessionStats {
	stats := SessionStats{Total: len(sessions)}

	patientMap := make(map[uint]bool)
	therapistMap := make(map[uint]bool)
	totalDuration := 0

	for _, session := range sessions {
		switch session.Estado {
		case "completada":
			stats.Completed++
		case "cancelada":
			stats.Cancelled++
		case "en_progreso":
			stats.InProgress++
		}

		totalDuration += session.Duracion
		patientMap[session.PacienteID] = true
		therapistMap[session.TerapeutaID] = true
	}

	if stats.Total > 0 {
		stats.AverageDuration = totalDuration / stats.Total
	}
	stats.UniquePatients = len(patientMap)
	stats.UniqueTerapists = len(therapistMap)

	return stats
}

func (s *ReportService) generateSessionConclusions(session *models.TherapySession, stats SessionStats) string {
	status := "programada"
	if session.Estado == "completada" {
		status = "completada exitosamente"
	} else if session.Estado == "cancelada" {
		status = "cancelada"
	}

	return fmt.Sprintf("La sesión #%d fue %s con una duración de %d minutos. "+
		"Participaron el paciente y el terapeuta asignado. "+
		"Los objetivos planteados fueron trabajados según lo establecido.",
		session.ID, status, session.Duracion)
}

func (s *ReportService) generateSessionRecommendations(session *models.TherapySession, stats SessionStats) string {
	if session.Estado == "cancelada" {
		return "Se recomienda reprogramar la sesión a la brevedad posible y mantener la continuidad del tratamiento."
	}
	return "Continuar con el plan de tratamiento establecido. Mantener la regularidad de las sesiones y documentar el progreso del paciente."
}

func (s *ReportService) generateGeneralConclusions(sessions []models.TherapySession, stats SessionStats) string {
	completionRate := 0
	if stats.Total > 0 {
		completionRate = (stats.Completed * 100) / stats.Total
	}

	return fmt.Sprintf("Durante el período analizado se registraron %d sesiones terapéuticas. "+
		"Se logró una tasa de completitud del %d%% con %d sesiones completadas exitosamente. "+
		"Se atendieron %d pacientes únicos con un promedio de duración de %d minutos por sesión.",
		stats.Total, completionRate, stats.Completed, stats.UniquePatients, stats.AverageDuration)
}

func (s *ReportService) generateGeneralRecommendations(stats SessionStats) string {
	recommendations := []string{}

	if stats.Cancelled > stats.Total/5 {
		recommendations = append(recommendations, "Reducir la tasa de cancelaciones mediante recordatorios previos")
	}

	if stats.AverageDuration < 45 {
		recommendations = append(recommendations, "Considerar extender la duración de las sesiones para mayor efectividad")
	}

	recommendations = append(recommendations,
		"Mantener el seguimiento regular con los pacientes activos",
		"Documentar detalladamente las observaciones en cada sesión")

	return strings.Join(recommendations, ". ") + "."
}

func (s *ReportService) generatePatientConclusions(patient *models.Patient, sessions []models.TherapySession, stats SessionStats) string {
	return fmt.Sprintf("El paciente %s ha participado en %d sesiones terapéuticas. "+
		"Se completaron %d sesiones con un promedio de duración de %d minutos. "+
		"El progreso del paciente muestra tendencias positivas en las áreas evaluadas.",
		patient.NombresApellidos, stats.Total, stats.Completed, stats.AverageDuration)
}

func (s *ReportService) generatePatientRecommendations(patient *models.Patient, stats SessionStats) string {
	return "Continuar con el tratamiento según el plan establecido. " +
		"Evaluar periódicamente el progreso y ajustar objetivos según sea necesario."
}

func (s *ReportService) generateTherapistConclusions(therapist *models.Usuarios, sessions []models.TherapySession, stats SessionStats) string {
	completionRate := 0
	if stats.Total > 0 {
		completionRate = (stats.Completed * 100) / stats.Total
	}

	return fmt.Sprintf("El terapeuta %s ha conducido %d sesiones con una tasa de completitud del %d%%. "+
		"Atendió a %d pacientes únicos con un promedio de duración de %d minutos por sesión.",
		therapist.Nombres_Apellidos, stats.Total, completionRate, stats.UniquePatients, stats.AverageDuration)
}

func (s *ReportService) generateTherapistRecommendations(therapist *models.Usuarios, stats SessionStats) string {
	return "Mantener la calidad del servicio terapéutico. " +
		"Continuar con el seguimiento apropiado de cada paciente y la documentación completa de las sesiones."
}

func (s *ReportService) toReportResponses(reports []models.Report) []dto.ReportResponse {
	responses := make([]dto.ReportResponse, len(reports))
	for i, report := range reports {
		responses[i] = s.ToReportResponse(&report)
	}
	return responses
}

func (s *ReportService) ToReportResponse(report *models.Report) dto.ReportResponse {
	response := dto.ReportResponse{
		ID:                report.ID,
		Type:              string(report.Type),
		Format:            string(report.Format),
		Status:            string(report.Status),
		Title:             report.Title,
		Description:       report.Description,
		GeneratedBy:       report.GeneratedBy,
		TherapistID:       report.TherapistID,
		PatientID:         report.PatientID,
		SessionID:         report.SessionID,
		StartDate:         report.StartDate,
		EndDate:           report.EndDate,
		FilePath:          report.FilePath,
		FileSize:          report.FileSize,
		TotalSessions:     report.TotalSessions,
		CompletedSessions: report.CompletedSessions,
		CancelledSessions: report.CancelledSessions,
		AverageDuration:   report.AverageDuration,
		Conclusions:       report.Conclusions,
		Recommendations:   report.Recommendations,
		Metadata:          map[string]interface{}(report.Metadata),
		ProcessedAt:       report.ProcessedAt,
		CreatedAt:         report.CreatedAt,
		UpdatedAt:         report.UpdatedAt,
	}

	if report.GeneratedByUser != nil {
		response.GeneratedByUser = &dto.UserBasicInfo{
			ID:               report.GeneratedByUser.ID,
			NombresApellidos: report.GeneratedByUser.Nombres_Apellidos,
			Correo:           report.GeneratedByUser.Correo,
		}
	}

	if report.Therapist != nil {
		response.Therapist = &dto.UserBasicInfo{
			ID:               report.Therapist.ID,
			NombresApellidos: report.Therapist.Nombres_Apellidos,
			Correo:           report.Therapist.Correo,
		}
	}

	if report.Patient != nil {
		response.Patient = &struct {
			ID               uint   `json:"id"`
			NombresApellidos string `json:"nombres_apellidos"`
			SerialID         string `json:"serial_id"`
		}{
			ID:               report.Patient.ID,
			NombresApellidos: report.Patient.NombresApellidos,
			SerialID:         report.Patient.SerialID,
		}
	}

	if report.FilePath != "" && report.Status == models.ReportStatusCompleted {
		response.DownloadURL = fmt.Sprintf("/api/v1/reports/%d/download", report.ID)
	}

	return response
}

func (s *ReportService) getSessionEmotionMetadata(report *models.Report) *sessionEmotionSummary {
	if report == nil || report.Metadata == nil {
		return nil
	}
	raw, exists := report.Metadata["session_emotion"]
	if !exists || raw == nil {
		return nil
	}
	data, err := json.Marshal(raw)
	if err != nil {
		return nil
	}
	var summary sessionEmotionSummary
	if err := json.Unmarshal(data, &summary); err != nil {
		return nil
	}
	return &summary
}

func (s *ReportService) getPatientEmotionMetadata(report *models.Report) []patientEmotionSummary {
	if report == nil || report.Metadata == nil {
		return nil
	}
	raw, exists := report.Metadata["patient_emotions"]
	if !exists || raw == nil {
		return nil
	}
	data, err := json.Marshal(raw)
	if err != nil {
		return nil
	}
	var rows []patientEmotionSummary
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil
	}
	return rows
}

func (s *ReportService) getSessionEmotionRows(report *models.Report) map[uint]sessionEmotionSummary {
	if report == nil || report.Metadata == nil {
		return nil
	}
	raw, exists := report.Metadata["session_emotions"]
	if !exists || raw == nil {
		return nil
	}
	data, err := json.Marshal(raw)
	if err != nil {
		return nil
	}
	var rows []sessionEmotionSummary
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil
	}
	result := make(map[uint]sessionEmotionSummary, len(rows))
	for _, row := range rows {
		result[row.SessionID] = row
	}
	return result
}

func (s *ReportService) addSessionDetailsToPDF(pdf *gofpdf.Fpdf, session *models.TherapySession) {
	pdf.SetFont("Arial", "B", 12)
	pdf.CellFormat(0, 8, "Detalles de la Sesión", "0", 1, "", false, 0, "")

	pdf.SetFont("Arial", "", 11)
	pdf.CellFormat(50, 6, "Fecha:", "0", 0, "", false, 0, "")
	pdf.CellFormat(0, 6, session.FechaSesion.Format("02/01/2006"), "0", 1, "", false, 0, "")

	pdf.CellFormat(50, 6, "Hora:", "0", 0, "", false, 0, "")
	pdf.CellFormat(0, 6, fmt.Sprintf("%s - %s", session.HoraInicio, session.HoraFin), "0", 1, "", false, 0, "")

	pdf.CellFormat(50, 6, "Duración:", "0", 0, "", false, 0, "")
	pdf.CellFormat(0, 6, fmt.Sprintf("%d minutos", session.Duracion), "0", 1, "", false, 0, "")

	pdf.CellFormat(50, 6, "Estado:", "0", 0, "", false, 0, "")
	pdf.CellFormat(0, 6, session.Estado, "0", 1, "", false, 0, "")

	if session.Ubicacion != "" {
		pdf.CellFormat(50, 6, "Ubicación:", "0", 0, "", false, 0, "")
		pdf.CellFormat(0, 6, session.Ubicacion, "0", 1, "", false, 0, "")
	}

	if session.Descripcion != "" {
		pdf.Ln(5)
		pdf.SetFont("Arial", "B", 12)
		pdf.CellFormat(0, 8, "Descripción", "0", 1, "", false, 0, "")
		pdf.SetFont("Arial", "", 11)
		pdf.MultiCell(0, 6, session.Descripcion, "0", "", false)
	}
}

func (s *ReportService) addSessionsListToPDF(pdf *gofpdf.Fpdf, report *models.Report, sessions []models.TherapySession) {
	sessionEmotions := s.getSessionEmotionRows(report)
	pdf.SetFont("Arial", "B", 12)
	pdf.CellFormat(0, 8, "Resumen de Sesiones", "0", 1, "", false, 0, "")
	pdf.Ln(2)

	pdf.SetFont("Arial", "B", 10)
	pdf.CellFormat(24, 7, "Fecha", "1", 0, "C", false, 0, "")
	pdf.CellFormat(34, 7, "Paciente", "1", 0, "C", false, 0, "")
	pdf.CellFormat(34, 7, "Terapeuta", "1", 0, "C", false, 0, "")
	pdf.CellFormat(18, 7, "Duración", "1", 0, "C", false, 0, "")
	pdf.CellFormat(28, 7, "Estado", "1", 0, "C", false, 0, "")
	pdf.CellFormat(32, 7, "Emoción", "1", 0, "C", false, 0, "")
	pdf.CellFormat(20, 7, "%", "1", 1, "C", false, 0, "")

	pdf.SetFont("Arial", "", 9)
	for _, session := range sessions {
		if len(sessions) > 10 && pdf.GetY() > 250 {
			pdf.AddPage()
			pdf.SetFont("Arial", "B", 10)
			pdf.CellFormat(24, 7, "Fecha", "1", 0, "C", false, 0, "")
			pdf.CellFormat(34, 7, "Paciente", "1", 0, "C", false, 0, "")
			pdf.CellFormat(34, 7, "Terapeuta", "1", 0, "C", false, 0, "")
			pdf.CellFormat(18, 7, "Duración", "1", 0, "C", false, 0, "")
			pdf.CellFormat(28, 7, "Estado", "1", 0, "C", false, 0, "")
			pdf.CellFormat(32, 7, "Emoción", "1", 0, "C", false, 0, "")
			pdf.CellFormat(20, 7, "%", "1", 1, "C", false, 0, "")
			pdf.SetFont("Arial", "", 9)
		}

		pdf.CellFormat(24, 6, session.FechaSesion.Format("02/01/2006"), "1", 0, "C", false, 0, "")

		pacienteName := "N/A"
		if session.Paciente.ID != 0 {
			pacienteName = truncateString(session.Paciente.NombresApellidos, 20)
		}
		pdf.CellFormat(34, 6, pacienteName, "1", 0, "C", false, 0, "")

		terapeutaName := "N/A"
		if session.Terapeuta.ID != 0 {
			terapeutaName = truncateString(session.Terapeuta.Nombres_Apellidos, 20)
		}
		pdf.CellFormat(34, 6, terapeutaName, "1", 0, "C", false, 0, "")

		emotionText := "-"
		confidenceText := "-"
		if emotion, exists := sessionEmotions[session.ID]; exists {
			emotionText = truncateString(emotion.Emotion, 15)
			confidenceText = emotion.ConfidenceText
		}

		pdf.CellFormat(18, 6, fmt.Sprintf("%d min", session.Duracion), "1", 0, "C", false, 0, "")
		pdf.CellFormat(28, 6, session.Estado, "1", 0, "C", false, 0, "")
		pdf.CellFormat(32, 6, emotionText, "1", 0, "C", false, 0, "")
		pdf.CellFormat(20, 6, confidenceText, "1", 1, "C", false, 0, "")
	}
}

func (s *ReportService) buildHTMLReport(report *models.Report, session *models.TherapySession, sessions []models.TherapySession) string {
	var html strings.Builder

	html.WriteString(`<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>` + report.Title + `</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #2196F3; border-bottom: 2px solid #2196F3; padding-bottom: 10px; }
        h2 { color: #1976D2; margin-top: 30px; }
        .info-row { margin: 10px 0; }
        .label { font-weight: bold; display: inline-block; width: 150px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th { background: #2196F3; color: white; padding: 10px; text-align: left; }
        td { padding: 8px; border-bottom: 1px solid #ddd; }
        .footer { margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; text-align: center; color: #666; }
    </style>
</head>
<body>
    <div class="container">
        <h1>` + report.Title + `</h1>`)

	if report.Description != "" {
		html.WriteString(`<p>` + report.Description + `</p>`)
	}

	if session != nil {
		html.WriteString(`<h2>Detalles de la Sesión</h2>`)
		html.WriteString(`<div class="info-row"><span class="label">Fecha:</span> ` + session.FechaSesion.Format("02/01/2006") + `</div>`)
		html.WriteString(`<div class="info-row"><span class="label">Horario:</span> ` + session.HoraInicio + ` - ` + session.HoraFin + `</div>`)
		html.WriteString(`<div class="info-row"><span class="label">Duración:</span> ` + fmt.Sprintf("%d minutos", session.Duracion) + `</div>`)
		html.WriteString(`<div class="info-row"><span class="label">Estado:</span> ` + session.Estado + `</div>`)
		if emotion := s.getSessionEmotionMetadata(report); emotion != nil {
			html.WriteString(`<h2>Estado Emocional</h2>`)
			html.WriteString(`<div class="info-row"><span class="label">Emoción dominante:</span> ` + emotion.Emotion + `</div>`)
			html.WriteString(`<div class="info-row"><span class="label">Confianza:</span> ` + emotion.ConfidenceText + `</div>`)
		}
	}

	if len(sessions) > 0 {
		sessionEmotions := s.getSessionEmotionRows(report)
		html.WriteString(`<h2>Lista de Sesiones</h2>
        <table>
            <thead>
                <tr>
                    <th>Fecha</th>
                    <th>Paciente</th>
                    <th>Duración</th>
                    <th>Estado</th>
                    <th>Emoción</th>
                    <th>Confianza</th>
                </tr>
            </thead>
            <tbody>`)

		for _, s := range sessions {
			pacienteName := "N/A"
			if s.Paciente.ID != 0 {
				pacienteName = s.Paciente.NombresApellidos
			}
			emotionText := "-"
			confidenceText := "-"
			if emotion, exists := sessionEmotions[s.ID]; exists {
				emotionText = emotion.Emotion
				confidenceText = emotion.ConfidenceText
			}

			html.WriteString(`<tr>`)
			html.WriteString(`<td>` + s.FechaSesion.Format("02/01/2006") + `</td>`)
			html.WriteString(`<td>` + pacienteName + `</td>`)
			html.WriteString(`<td>` + fmt.Sprintf("%d min", s.Duracion) + `</td>`)
			html.WriteString(`<td>` + s.Estado + `</td>`)
			html.WriteString(`<td>` + emotionText + `</td>`)
			html.WriteString(`<td>` + confidenceText + `</td>`)
			html.WriteString(`</tr>`)
		}

		html.WriteString(`</tbody></table>`)

		patientEmotions := s.getPatientEmotionMetadata(report)
		if len(patientEmotions) > 0 {
			html.WriteString(`<h2>Estado Emocional por Paciente</h2><table><thead><tr><th>Paciente</th><th>Emoción</th><th>Confianza</th></tr></thead><tbody>`)
			for _, item := range patientEmotions {
				html.WriteString(`<tr>`)
				html.WriteString(`<td>` + item.PatientName + `</td>`)
				html.WriteString(`<td>` + item.Emotion + `</td>`)
				html.WriteString(`<td>` + item.ConfidenceText + `</td>`)
				html.WriteString(`</tr>`)
			}
			html.WriteString(`</tbody></table>`)
		}
	}

	if report.Conclusions != "" {
		html.WriteString(`<h2>Conclusiones</h2><p>` + report.Conclusions + `</p>`)
	}

	if report.Recommendations != "" {
		html.WriteString(`<h2>Recomendaciones</h2><p>` + report.Recommendations + `</p>`)
	}

	html.WriteString(`<div class="footer">Generado el ` + time.Now().Format("02/01/2006 15:04") + `</div>`)
	html.WriteString(`</div></body></html>`)

	return html.String()
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}
