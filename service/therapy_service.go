package services

import (
	"encoding/base64"
	"errors"
	"fmt"
	"log"
	"math"
	"strings"
	"time"
	"usuarios/dto"
	"usuarios/models"
	"usuarios/utils"
	"gorm.io/gorm"
)

type TherapyService struct {
	db           *gorm.DB
	emailService *EmailService
	mlClient     *utils.MLServiceClient
}

func NewTherapyService(db *gorm.DB) *TherapyService {
	return &TherapyService{
		db:           db,
		mlClient:     utils.NewMLServiceClient(),
		emailService: NewEmailService(),
	}
}

func (s *TherapyService) Create(dto *dto.CreateTherapySessionDTO, userID uint) (*models.TherapySession, error) {
	// Determinar el terapeuta asignado
	var assignedTherapistID uint
	if dto.TerapeutaID != nil && *dto.TerapeutaID > 0 {
		// Si se especifica un terapeuta en el DTO, usarlo (para administradores)
		assignedTherapistID = *dto.TerapeutaID

		// Verificar que el terapeuta existe y tiene el rol correcto
		if err := s.validateTherapistRole(assignedTherapistID); err != nil {
			return nil, err
		}
	} else {
		// Si no se especifica, usar el usuario actual (debe ser terapeuta)
		assignedTherapistID = userID
		if err := s.validateTherapistRole(assignedTherapistID); err != nil {
			return nil, err
		}
	}
	fechaSesion, err := time.Parse("2006-01-02", dto.FechaSesion)
	if err != nil {
		return nil, errors.New("formato de fecha inválido")
	}

	if fechaSesion.Before(time.Now().AddDate(0, 0, -1)) {
		return nil, errors.New("la fecha no puede ser anterior a ayer")
	}

	duracion, err := s.calculateDuration(dto.HoraInicio, dto.HoraFin)
	if err != nil {
		return nil, errors.New("formato de hora inválido")
	}

	if duracion < 30 || duracion > 45 {
		return nil, errors.New("la duración debe ser entre 30 y 45 minutos")
	}

	if err := s.validatePatientAccess(dto.PacienteID, assignedTherapistID); err != nil {
		return nil, err
	}

	if err := s.validateTherapistPatientLimit(assignedTherapistID); err != nil {
		return nil, err
	}

	if err := s.validatePatientSessionLimit(dto.PacienteID); err != nil {
		return nil, err
	}

	if err := s.checkDuplicateSession(dto.PacienteID, fechaSesion, dto.HoraInicio, dto.Descripcion, dto.Objetivos); err != nil {
		return nil, err
	}

	session := &models.TherapySession{
		PacienteID:     dto.PacienteID,
		TerapeutaID:    assignedTherapistID,
		FechaSesion:    fechaSesion,
		HoraInicio:     dto.HoraInicio,
		HoraFin:        dto.HoraFin,
		Duracion:       duracion,
		Ubicacion:      dto.Ubicacion,
		Direccion:      dto.Direccion,
		Descripcion:    dto.Descripcion,
		Objetivos:      models.StringArray(dto.Objetivos),
		Materiales:     models.StringArray(dto.Materiales),
		TipoSesion:     dto.TipoSesion,
		Modalidad:      dto.Modalidad,
		Estado:         "programada",
	}

	if err := s.db.Create(session).Error; err != nil {
		return nil, err
	}

	result, err := s.GetByID(session.ID, assignedTherapistID, []string{"TR"})
	if err != nil {
		return nil, err
	}

	go s.sendCreationNotification(result)
	go s.scheduleReminders(result)

	return result, nil
}

func (s *TherapyService) GetAll(userID uint, roles []string) ([]models.TherapySession, error) {
	var sessions []models.TherapySession
	query := s.db.
		Preload("Paciente").
		Preload("Paciente.Cuidador", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil").Where("activo = ?", false)
		}).
		Preload("Terapeuta", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Where("is_deleted = ?", false)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Joins("JOIN patients ON patients.id = therapy_sessions.paciente_id").
			Where("patients.cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("terapeuta_id = ?", userID)
	}

	if err := query.Order("fecha_sesion DESC, hora_inicio ASC").Find(&sessions).Error; err != nil {
		return nil, err
	}

	
	for i, session := range sessions {
		if session.Paciente.CuidadorID != nil {
			_ = session.Paciente.Cuidador != nil
		}
		if i >= 3 {
			break
		}
	}

	return sessions, nil
}

func (s *TherapyService) GetPaginated(userID uint, roles []string, search *dto.SearchSessionsDTO) (*dto.PaginatedSessionsResponse, error) {
	var sessions []models.TherapySession
	var total int64

	query := s.db.
		Preload("Paciente").
		Preload("Paciente.Cuidador", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil").Where("activo = ?", false)
		}).
		Preload("Terapeuta", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Where("is_deleted = ?", false)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Joins("JOIN patients ON patients.id = therapy_sessions.paciente_id").
			Where("patients.cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("terapeuta_id = ?", userID)
	}

	if search.Query != "" {
		searchPattern := "%" + search.Query + "%"
		query = query.Where(
			"descripcion LIKE ? OR ubicacion LIKE ? OR JSON_EXTRACT(objetivos, '$') LIKE ?",
			searchPattern, searchPattern, searchPattern,
		)
	}

	if search.Estado != "" {
		query = query.Where("estado = ?", search.Estado)
	}

	if search.PatientID != 0 {
		query = query.Where("paciente_id = ?", search.PatientID)
	}

	if err := query.Model(&models.TherapySession{}).Count(&total).Error; err != nil {
		return nil, err
	}

	if search.Limit <= 0 {
		search.Limit = 5
	}
	if search.Limit > 5 {
		search.Limit = 5
	}

	offset := (search.Page - 1) * search.Limit
	if err := query.Offset(offset).Limit(search.Limit).Order("fecha_sesion DESC, hora_inicio ASC").Find(&sessions).Error; err != nil {
		return nil, err
	}

	totalPages := int(math.Ceil(float64(total) / float64(search.Limit)))

	sessionResponses := make([]dto.SessionResponse, len(sessions))
	for i, session := range sessions {
		sessionResponses[i] = s.ToSessionResponse(session)
	}

	return &dto.PaginatedSessionsResponse{
		Sessions:    sessionResponses,
		Total:       total,
		Page:        search.Page,
		Limit:       search.Limit,
		TotalPages:  totalPages,
		HasNext:     search.Page < totalPages,
		HasPrevious: search.Page > 1,
	}, nil
}

func (s *TherapyService) GetByID(id, userID uint, roles []string) (*models.TherapySession, error) {
	var session models.TherapySession
	query := s.db.
		Preload("Paciente").
		Preload("Paciente.Cuidador", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil").Where("activo = ?", false)
		}).
		Preload("Terapeuta", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("TerapeutaReasignado", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Where("is_deleted = ?", false)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Joins("JOIN patients ON patients.id = therapy_sessions.paciente_id").
			Where("patients.cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("terapeuta_id = ?", userID)
	}

	err := query.First(&session, id).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errors.New("sesión no encontrada")
		}
		return nil, err
	}

	
	if session.Paciente.CuidadorID != nil {
		_ = session.Paciente.Cuidador != nil
	}

	return &session, nil
}

func (s *TherapyService) GetPatientSessions(patientID, userID uint, roles []string) ([]models.TherapySession, error) {
	
	var patientCount int64
	if err := s.db.Model(&models.Patient{}).Where("id = ?", patientID).Count(&patientCount).Error; err != nil {
		
		return nil, err
	}

	var totalSessionCount int64
	if err := s.db.Model(&models.TherapySession{}).Where("paciente_id = ? AND is_deleted = ?", patientID, false).Count(&totalSessionCount).Error; err != nil {
	
		return nil, err
	}
	

	var sessions []models.TherapySession
	query := s.db.
		Preload("Paciente").
		Preload("Paciente.Cuidador", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil").Where("activo = ?", false)
		}).
		Preload("Terapeuta", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Where("is_deleted = ? AND paciente_id = ?", false, patientID)

	// Determinar qué filtros aplicar
	isAdmin := s.hasRole(roles, "AD")
	isTherapist := s.hasRole(roles, "TR")
	isCaregiver := s.hasRole(roles, "PD")

	log.Printf("[DEBUG] Role analysis: isAdmin=%v, isTherapist=%v, isCaregiver=%v", isAdmin, isTherapist, isCaregiver)

	var finalQuery *gorm.DB = query
	if isCaregiver && !isTherapist && !isAdmin {
		// Solo cuidadores: filtrar por sesiones de sus pacientes
		log.Printf("[DEBUG] Applying caregiver filter: cuidador_id = %d", userID)
		finalQuery = query.Joins("JOIN patients ON patients.id = therapy_sessions.paciente_id").
			Where("patients.cuidador_id = ?", userID)

		// Debug: Check if this patient has the current user as caregiver
		var patient models.Patient
		if err := s.db.Where("id = ?", patientID).First(&patient).Error; err == nil {
			log.Printf("[DEBUG] Patient %d caregiver ID: %v (user ID: %d)", patientID, patient.CuidadorID, userID)
			if patient.CuidadorID != nil {
				log.Printf("[DEBUG] Caregiver match: %v", *patient.CuidadorID == userID)
			}
		}
	} else if isTherapist && !isAdmin {
		// Solo terapeutas (no administradores): filtrar por sus sesiones
		log.Printf("[DEBUG] Applying therapist filter: terapeuta_id = %d", userID)
		finalQuery = query.Where("terapeuta_id = ?", userID)
	} else {
		// Administradores: sin filtros adicionales
		log.Printf("[DEBUG] No additional filters applied (admin access)")
		finalQuery = query
	}

	// Count filtered results before executing
	var filteredCount int64
	if err := finalQuery.Model(&models.TherapySession{}).Count(&filteredCount).Error; err != nil {
		log.Printf("[ERROR] Failed to count filtered sessions: %v", err)
	} else {
		log.Printf("[DEBUG] Filtered session count: %d", filteredCount)
	}

	if err := finalQuery.Order("fecha_sesion DESC, hora_inicio ASC").Find(&sessions).Error; err != nil {
		log.Printf("[ERROR] Database query failed: %v", err)
		return nil, err
	}

	log.Printf("[DEBUG] GetPatientSessions loaded %d sessions for patient %d", len(sessions), patientID)

	// Log detallado de las sesiones encontradas
	for i, session := range sessions {
		log.Printf("[DEBUG] Session %d: ID=%d, Patient=%d, Therapist=%d, Date=%s",
			i+1, session.ID, session.PacienteID, session.TerapeutaID, session.FechaSesion.Format("2006-01-02"))
		if i >= 3 { // Limitar logs para no saturar
			break
		}
	}

	// If we found 0 sessions but total > 0, there's a filtering issue
	if len(sessions) == 0 && totalSessionCount > 0 {
		log.Printf("[WARNING] Filter logic is blocking %d available sessions for patient %d (user %d, roles %v)",
			totalSessionCount, patientID, userID, roles)
	}

	return sessions, nil
}

func (s *TherapyService) Update(id uint, dto *dto.UpdateTherapySessionDTO, userID uint, roles []string) (*models.TherapySession, error) {
	session, err := s.GetByID(id, userID, roles)
	if err != nil {
		return nil, err
	}

	if !s.hasRole(roles, "AD") && session.TerapeutaID != userID {
		return nil, errors.New("solo el terapeuta asignado puede editar la sesión")
	}

	if session.UpdateCount >= 3 {
		return nil, errors.New("la sesión ya ha sido actualizada el máximo de veces permitidas")
	}

	updates := make(map[string]interface{})
	updates["update_count"] = session.UpdateCount + 1

	if dto.FechaSesion != "" {
		fechaSesion, err := time.Parse("2006-01-02", dto.FechaSesion)
		if err != nil {
			return nil, errors.New("formato de fecha inválido")
		}
		updates["fecha_sesion"] = fechaSesion
	}

	if dto.HoraInicio != "" {
		updates["hora_inicio"] = dto.HoraInicio
	}
	if dto.HoraFin != "" {
		updates["hora_fin"] = dto.HoraFin
	}

	if dto.HoraInicio != "" && dto.HoraFin != "" {
		duracion, err := s.calculateDuration(dto.HoraInicio, dto.HoraFin)
		if err == nil && duracion >= 30 && duracion <= 45 {
			updates["duracion"] = duracion
		}
	}

	if dto.Ubicacion != "" {
		updates["ubicacion"] = dto.Ubicacion
	}
	if dto.Direccion != "" {
		updates["direccion"] = dto.Direccion
	}
	if dto.Descripcion != "" {
		updates["descripcion"] = dto.Descripcion
	}
	if len(dto.Objetivos) > 0 {
		updates["objetivos"] = models.StringArray(dto.Objetivos)
	}
	if len(dto.Materiales) > 0 {
		updates["materiales"] = models.StringArray(dto.Materiales)
	}
	if dto.NotasTerapeuta != "" {
		updates["notas_terapeuta"] = dto.NotasTerapeuta
	}
	if dto.Estado != "" {
		updates["estado"] = dto.Estado
	}
	if dto.TipoSesion != "" {
		updates["tipo_sesion"] = dto.TipoSesion
	}
	if dto.Modalidad != "" {
		updates["modalidad"] = dto.Modalidad
	}

	if err := s.db.Model(session).Updates(updates).Error; err != nil {
		return nil, err
	}

	return s.GetByID(id, userID, roles)
}

func (s *TherapyService) Reschedule(id uint, dto *dto.RescheduleSessionDTO, userID uint, roles []string) (*models.TherapySession, error) {
	session, err := s.GetByID(id, userID, roles)
	if err != nil {
		return nil, err
	}

	if !s.hasRole(roles, "AD") && session.TerapeutaID != userID {
		return nil, errors.New("solo el terapeuta asignado puede reprogramar la sesión")
	}

	if session.UpdateCount >= 3 {
		return nil, errors.New("la sesión ya ha sido actualizada el máximo de veces permitidas")
	}

	fechaSesion, err := time.Parse("2006-01-02", dto.FechaSesion)
	if err != nil {
		return nil, errors.New("formato de fecha inválido")
	}

	duracion, err := s.calculateDuration(dto.HoraInicio, dto.HoraFin)
	if err != nil {
		return nil, errors.New("formato de hora inválido")
	}

	if duracion < 30 || duracion > 45 {
		return nil, errors.New("la duración debe ser entre 30 y 45 minutos")
	}

	updates := map[string]interface{}{
		"fecha_sesion":  fechaSesion,
		"hora_inicio":   dto.HoraInicio,
		"hora_fin":      dto.HoraFin,
		"duracion":      duracion,
		"update_count":  session.UpdateCount + 1,
	}

	if err := s.db.Model(session).Updates(updates).Error; err != nil {
		return nil, err
	}

	result, err := s.GetByID(id, userID, roles)
	if err != nil {
		return nil, err
	}

	go s.sendRescheduleNotification(result, dto.Motivo)
	go s.scheduleReminders(result)

	return result, nil
}

func (s *TherapyService) Delete(id, userID uint, roles []string) error {
	session, err := s.GetByID(id, userID, roles)
	if err != nil {
		return err
	}

	if !s.hasRole(roles, "AD") && session.TerapeutaID != userID {
		return errors.New("solo el terapeuta asignado puede eliminar la sesión")
	}

	if session.IsDeleted {
		return errors.New("la sesión ya ha sido eliminada")
	}

	if err := s.db.Model(session).Update("is_deleted", true).Error; err != nil {
		return err
	}

	go s.sendCancellationNotification(session, "Sesión cancelada por el terapeuta")

	return nil
}

func (s *TherapyService) GetLatestPatients(userID uint, roles []string) ([]dto.PatientListDTO, error) {
	var patients []models.Patient
	query := s.db.
		Preload("Terapeuta", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos")
		}).
		Preload("Cuidador", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos")
		}).
		Limit(3).
		Order("created_at DESC")

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("terapeuta_id = ?", userID)
	}

	if err := query.Find(&patients).Error; err != nil {
		return nil, err
	}

	// Transform to DTO with calculated age
	patientDTOs := make([]dto.PatientListDTO, len(patients))
	for i, patient := range patients {
		patientDTOs[i] = s.toPatientListDTO(patient)
	}

	return patientDTOs, nil
}

func (s *TherapyService) validatePatientAccess(patientID, terapeutaID uint) error {
	var patient models.Patient
	err := s.db.Where("id = ? AND terapeuta_id = ?", patientID, terapeutaID).First(&patient).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return errors.New("paciente no encontrado o no tiene acceso")
		}
		return err
	}
	return nil
}

func (s *TherapyService) validateTherapistPatientLimit(terapeutaID uint) error {
	var count int64
	if err := s.db.Model(&models.Patient{}).Where("terapeuta_id = ?", terapeutaID).Count(&count).Error; err != nil {
		return err
	}
	if count >= 20 {
		return errors.New("el terapeuta ha alcanzado el límite máximo de 20 pacientes")
	}
	return nil
}

func (s *TherapyService) validatePatientSessionLimit(patientID uint) error {
	var count int64
	if err := s.db.Model(&models.TherapySession{}).Where("paciente_id = ? AND is_deleted = ?", patientID, false).Count(&count).Error; err != nil {
		return err
	}
	if count >= 8 {
		return errors.New("el paciente ha alcanzado el límite máximo de 8 sesiones terapéuticas")
	}
	return nil
}

func (s *TherapyService) checkDuplicateSession(patientID uint, fecha time.Time, horaInicio string, descripcion string, objetivos []string) error {
	var count int64
	query := s.db.Model(&models.TherapySession{}).Where(
		"paciente_id = ? AND fecha_sesion = ? AND hora_inicio = ? AND is_deleted = ?",
		patientID, fecha, horaInicio, false,
	)

	if err := query.Count(&count).Error; err != nil {
		return err
	}

	if count > 0 {
		return errors.New("ya existe una sesión programada para este paciente en la misma fecha y hora")
	}

	var existing models.TherapySession
	err := s.db.Where(
		"paciente_id = ? AND descripcion = ? AND JSON_EXTRACT(objetivos, '$') = ? AND is_deleted = ?",
		patientID, descripcion, fmt.Sprintf(`["%s"]`, strings.Join(objetivos, `", "`)), false,
	).First(&existing).Error

	if err == nil {
		return errors.New("ya existe una sesión con la misma descripción y objetivos para este paciente")
	}

	return nil
}

func (s *TherapyService) calculateDuration(horaInicio, horaFin string) (int, error) {
	inicio, err := time.Parse("15:04", horaInicio)
	if err != nil {
		return 0, err
	}

	fin, err := time.Parse("15:04", horaFin)
	if err != nil {
		return 0, err
	}

	duracion := fin.Sub(inicio)
	return int(duracion.Minutes()), nil
}

func (s *TherapyService) ToSessionResponse(session models.TherapySession) dto.SessionResponse {

	log.Printf("[DEBUG] Session ID: %d, Paciente: %s, PacienteID: %d",
		session.ID, session.Paciente.NombresApellidos, session.Paciente.ID)

	if session.Paciente.CuidadorID != nil {
		log.Printf("[DEBUG] Paciente tiene CuidadorID: %d", *session.Paciente.CuidadorID)
		if session.Paciente.Cuidador != nil {
			log.Printf("[DEBUG] Cuidador cargado: %s (ID: %d)",
				session.Paciente.Cuidador.Nombres_Apellidos, session.Paciente.Cuidador.ID)
		} else {
			log.Printf("[WARNING] CuidadorID existe pero Cuidador es nil - problema de preload")

	
			var cuidadorDirect models.Usuarios
			err := s.db.Where("idusuario = ?", *session.Paciente.CuidadorID).First(&cuidadorDirect).Error
			if err != nil {
				log.Printf("[DEBUG] ❌ Consulta directa falló: %v", err)
			} else {
				log.Printf("[DEBUG] ✅ Consulta directa exitosa: %s (activo: %v, idusuario: %d)",
					cuidadorDirect.Nombres_Apellidos, cuidadorDirect.Activo, cuidadorDirect.ID)
			}
		}
	} else {
		log.Printf("[DEBUG] Paciente sin cuidador asignado")
	}

	response := dto.SessionResponse{
		ID:             session.ID,
		PacienteID:     session.PacienteID,
		TerapeutaID:    session.TerapeutaID,
		FechaSesion:    session.FechaSesion.Format("2006-01-02"),
		HoraInicio:     session.HoraInicio,
		HoraFin:        session.HoraFin,
		Duracion:       session.Duracion,
		Ubicacion:      session.Ubicacion,
		Direccion:      session.Direccion,
		Descripcion:    session.Descripcion,
		Objetivos:      []string(session.Objetivos),
		Materiales:     []string(session.Materiales),
		NotasTerapeuta: session.NotasTerapeuta,
		Estado:         session.Estado,
		TipoSesion:     session.TipoSesion,
		Modalidad:      session.Modalidad,
		UpdateCount:    session.UpdateCount,
		CreatedAt:      session.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt:      session.UpdatedAt.Format("2006-01-02 15:04:05"),
		Paciente: dto.PatientBasicInfo{
			ID:               session.Paciente.ID,
			NombresApellidos: session.Paciente.NombresApellidos,
			FotoMovil:        session.Paciente.FotoMovil,
		},
		Terapeuta: dto.UserBasicInfo{
			ID:                session.Terapeuta.ID,
			NombresApellidos:  session.Terapeuta.Nombres_Apellidos,
			Correo:            session.Terapeuta.Correo,
			Telefono:          session.Terapeuta.Telefono,
			FotoMovil:         session.Terapeuta.FotoMovil,
		},
	}


	if session.Paciente.Cuidador != nil {
		response.Cuidador = &dto.UserBasicInfo{
			ID:                session.Paciente.Cuidador.ID,
			NombresApellidos:  session.Paciente.Cuidador.Nombres_Apellidos,
			Correo:            session.Paciente.Cuidador.Correo,
			Telefono:          session.Paciente.Cuidador.Telefono,
			FotoMovil:         session.Paciente.Cuidador.FotoMovil,
		}
		log.Printf("[DEBUG] Cuidador agregado a respuesta: %s", response.Cuidador.NombresApellidos)
	} else if session.Paciente.CuidadorID != nil {

		log.Printf("[DEBUG] Intentando cargar cuidador manualmente (ID: %d)", *session.Paciente.CuidadorID)
		var cuidadorFallback models.Usuarios
		err := s.db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil").
			Where("idusuario = ? AND activo = ?", *session.Paciente.CuidadorID, false).
			First(&cuidadorFallback).Error
		if err == nil {
			response.Cuidador = &dto.UserBasicInfo{
				ID:                cuidadorFallback.ID,
				NombresApellidos:  cuidadorFallback.Nombres_Apellidos,
				Correo:            cuidadorFallback.Correo,
				Telefono:          cuidadorFallback.Telefono,
				FotoMovil:         cuidadorFallback.FotoMovil,
			}
			log.Printf("[DEBUG] ✅ Cuidador cargado manualmente: %s", response.Cuidador.NombresApellidos)
		} else {
			log.Printf("[DEBUG] ❌ No se pudo cargar cuidador manualmente: %v", err)
		}
	} else {
		log.Printf("[DEBUG] No se agregó cuidador a la respuesta")
	}


	var rating models.TherapistRating
	if err := s.db.Where("session_id = ?", session.ID).First(&rating).Error; err == nil {
		response.Rating = &dto.TherapistRatingResponse{
			ID:          rating.ID,
			SessionID:   rating.SessionID,
			TherapistID: rating.TherapistID,
			CaregiverID: rating.CaregiverID,
			PatientID:   rating.PatientID,
			Rating:      rating.Rating,
			Comment:     rating.Comment,
			CreatedAt:   rating.CreatedAt.Format("2006-01-02 15:04:05"),
			UpdatedAt:   rating.UpdatedAt.Format("2006-01-02 15:04:05"),
		}
		log.Printf("[DEBUG] ⭐ Calificación encontrada: %d estrellas", rating.Rating)
	}

	
	if session.TerapeutaReasignadoID != nil && *session.TerapeutaReasignadoID > 0 {
		// Si ya está precargado, usarlo directamente
		if session.TerapeutaReasignado != nil {
			response.TerapeutaReasignado = &dto.UserBasicInfo{
				ID:                session.TerapeutaReasignado.ID,
				NombresApellidos:  session.TerapeutaReasignado.Nombres_Apellidos,
				Correo:            session.TerapeutaReasignado.Correo,
				Telefono:          session.TerapeutaReasignado.Telefono,
				FotoMovil:         session.TerapeutaReasignado.FotoMovil,
			}
			log.Printf("[DEBUG] 🔄 Terapeuta reasignado (precargado): %s (ID: %d)",
				session.TerapeutaReasignado.Nombres_Apellidos, session.TerapeutaReasignado.ID)
		} else {

			var newTherapist models.Usuarios
			if err := s.db.First(&newTherapist, *session.TerapeutaReasignadoID).Error; err == nil {
				response.TerapeutaReasignado = &dto.UserBasicInfo{
					ID:                newTherapist.ID,
					NombresApellidos:  newTherapist.Nombres_Apellidos,
					Correo:            newTherapist.Correo,
					Telefono:          newTherapist.Telefono,
					FotoMovil:         newTherapist.FotoMovil,
				}
				log.Printf("[DEBUG] 🔄 Terapeuta reasignado (cargado): %s (ID: %d)", newTherapist.Nombres_Apellidos, newTherapist.ID)
			}
		}
	}

	return response
}

func (s *TherapyService) sendCreationNotification(session *models.TherapySession) {
	if session.Paciente.CuidadorID != nil {
		var cuidador models.Usuarios
		if err := s.db.First(&cuidador, *session.Paciente.CuidadorID).Error; err == nil {
			s.emailService.SendSessionCreatedEmail(session, &session.Paciente, &cuidador)
		}
	}
}

func (s *TherapyService) sendRescheduleNotification(session *models.TherapySession, motivo string) {
	if session.Paciente.CuidadorID != nil {
		var cuidador models.Usuarios
		if err := s.db.First(&cuidador, *session.Paciente.CuidadorID).Error; err == nil {
			s.emailService.SendSessionRescheduledEmail(session, &session.Paciente, &cuidador, motivo)
		}
	}
}

func (s *TherapyService) sendCancellationNotification(session *models.TherapySession, motivo string) {
	if session.Paciente.CuidadorID != nil {
		var cuidador models.Usuarios
		if err := s.db.First(&cuidador, *session.Paciente.CuidadorID).Error; err == nil {
			s.emailService.SendSessionCancelledEmail(session, &session.Paciente, &cuidador, motivo)
		}
	}
}

func (s *TherapyService) scheduleReminders(session *models.TherapySession) {
	dayBefore := session.FechaSesion.AddDate(0, 0, -1)
	sessionDateTime := time.Date(
		session.FechaSesion.Year(), session.FechaSesion.Month(), session.FechaSesion.Day(),
		getHourFromTimeString(session.HoraInicio), getMinuteFromTimeString(session.HoraInicio), 0, 0,
		session.FechaSesion.Location(),
	)
	twentyMinBefore := sessionDateTime.Add(-20 * time.Minute)

	if time.Now().Before(dayBefore) {
		time.AfterFunc(time.Until(dayBefore), func() {
			s.sendReminder(session, "day_before")
		})
	}

	if time.Now().Before(twentyMinBefore) {
		time.AfterFunc(time.Until(twentyMinBefore), func() {
			s.sendReminder(session, "20_minutes")
		})
	}
}

func (s *TherapyService) sendReminder(session *models.TherapySession, reminderType string) {
	if session.Paciente.CuidadorID != nil {
		var cuidador models.Usuarios
		if err := s.db.First(&cuidador, *session.Paciente.CuidadorID).Error; err == nil {
			s.emailService.SendSessionReminderEmail(session, &session.Paciente, &cuidador, reminderType)
		}
	}
}

func (s *TherapyService) GetAvailableTherapists() ([]dto.UserBasicInfo, error) {

	var therapists []models.Usuarios
	query := s.db.Table("usuarios").
		Select("DISTINCT usuarios.idusuario, usuarios.nombres_apellidos, usuarios.correo, usuarios.telefono, usuarios.foto_movil").
		Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
		Joins("JOIN roles ON user_roles.roles_id = roles.id").
		Where("roles.id = 4"). 
		Where("usuarios.activo = 0").
		Having("(SELECT COUNT(*) FROM patients WHERE terapeuta_id = usuarios.idusuario) < 20") 

	if err := query.Find(&therapists).Error; err != nil {
		log.Printf("[ERROR] Error al obtener terapeutas: %v", err)
		return nil, err
	}

	log.Printf("[DEBUG] Terapeutas disponibles encontrados: %d", len(therapists))
	for _, t := range therapists {

		var patientCount int64
		s.db.Model(&models.Patient{}).Where("terapeuta_id = ?", t.ID).Count(&patientCount)
		log.Printf("  - ID: %d, Nombre: %s, Email: %s, Pacientes: %d/20", t.ID, t.Nombres_Apellidos, t.Correo, patientCount)
	}

	result := make([]dto.UserBasicInfo, len(therapists))
	for i, therapist := range therapists {
		result[i] = dto.UserBasicInfo{
			ID:                therapist.ID,
			NombresApellidos:  therapist.Nombres_Apellidos,
			Correo:            therapist.Correo,
			Telefono:          therapist.Telefono,
			FotoMovil:         therapist.FotoMovil,
		}
	}

	return result, nil
}


func (s *TherapyService) toPatientListDTO(patient models.Patient) dto.PatientListDTO {

	now := time.Now()
	birthDate := time.Time(patient.FechaNacimiento)
	age := now.Year() - birthDate.Year()

	if now.YearDay() < birthDate.YearDay() {
		age--
	}


	if age < 0 {
		age = 0
	}

	var cuidadorNombre *string
	if patient.Cuidador != nil {
		cuidadorNombre = &patient.Cuidador.Nombres_Apellidos
	}

	return dto.PatientListDTO{
		ID:               patient.ID,
		SerialID:         patient.SerialID,
		NombresApellidos: patient.NombresApellidos,
		TipoDocumento:    patient.TipoDocumento,
		NumDocumento:     patient.NumDocumento,
		Edad:             age,
		Sexo:             patient.Sexo,
		TerapeutaNombre:  patient.Terapeuta.Nombres_Apellidos,
		CuidadorNombre:   cuidadorNombre,
		Activo:           patient.Activo,
		FotoMovil:        patient.FotoMovil,
	}
}


func (s *TherapyService) GetAllActiveUsers() ([]dto.UserBasicInfo, error) {
	var users []models.Usuarios

	query := s.db.Table("usuarios").
		Select("usuarios.idusuario, usuarios.nombres_apellidos, usuarios.correo, usuarios.telefono, usuarios.foto_movil").
		Where("usuarios.activo = 0").
		Order("usuarios.nombres_apellidos ASC")

	if err := query.Find(&users).Error; err != nil {
		log.Printf("[ERROR] Error al obtener usuarios activos: %v", err)
		return nil, err
	}

	log.Printf("[DEBUG] Usuarios activos encontrados: %d", len(users))

	result := make([]dto.UserBasicInfo, len(users))
	for i, user := range users {
		result[i] = dto.UserBasicInfo{
			ID:                user.ID,
			NombresApellidos:  user.Nombres_Apellidos,
			Correo:            user.Correo,
			Telefono:          user.Telefono,
			FotoMovil:         user.FotoMovil,
		}
	}

	return result, nil
}


func (s *TherapyService) DebugCaregiverPreload() error {
	var sessions []models.TherapySession


	err := s.db.Preload("Paciente").Preload("Paciente.Cuidador").Preload("Terapeuta").
		Where("is_deleted = ?", false).
		Limit(5).
		Find(&sessions).Error

	if err != nil {
		log.Printf("[ERROR] Debug query failed: %v", err)
		return err
	}

	log.Printf("[DEBUG] ===== CAREGIVER PRELOAD DEBUG =====")
	log.Printf("[DEBUG] Found %d sessions for testing", len(sessions))

	for _, session := range sessions {
		log.Printf("[DEBUG] Session ID: %d, Paciente: %s", session.ID, session.Paciente.NombresApellidos)
		log.Printf("[DEBUG] Paciente ID: %d", session.Paciente.ID)
		log.Printf("[DEBUG] CuidadorID from preload: %v", session.Paciente.CuidadorID)


		var patientDirect models.Patient
		err := s.db.Where("id = ?", session.Paciente.ID).First(&patientDirect).Error
		if err == nil {
			log.Printf("[DEBUG] 🔍 Consulta directa paciente - CuidadorID: %v", patientDirect.CuidadorID)
			if patientDirect.CuidadorID != nil {
				log.Printf("[DEBUG] 🔍 Valor directo CuidadorID: %d", *patientDirect.CuidadorID)
			}
		} else {
			log.Printf("[DEBUG] ❌ Error consultando paciente directamente: %v", err)
		}

		if session.Paciente.CuidadorID != nil {
			log.Printf("[DEBUG] CuidadorID value: %d", *session.Paciente.CuidadorID)
			if session.Paciente.Cuidador != nil {
				log.Printf("[DEBUG] ✅ Cuidador loaded: %s (ID: %d)",
					session.Paciente.Cuidador.Nombres_Apellidos, session.Paciente.Cuidador.ID)
			} else {
				log.Printf("[DEBUG] ❌ Cuidador NOT loaded despite CuidadorID being set")

				var cuidador models.Usuarios
				err := s.db.Where("idusuario = ?", *session.Paciente.CuidadorID).First(&cuidador).Error
				if err != nil {
					log.Printf("[DEBUG] ❌ Cuidador with ID %d not found in database: %v", *session.Paciente.CuidadorID, err)
				} else {
					log.Printf("[DEBUG] ✅ Cuidador exists in DB: %s (active: %v)", cuidador.Nombres_Apellidos, cuidador.Activo)
				}
			}
		} else {
			log.Printf("[DEBUG] No CuidadorID assigned")
		}
		log.Printf("[DEBUG] ----------------------------------------")
	}

	return nil
}

func (s *TherapyService) validateTherapistRole(therapistID uint) error {
	var therapist models.Usuarios
	err := s.db.Table("usuarios").
		Select("usuarios.idusuario, usuarios.nombres_apellidos").
		Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
		Joins("JOIN roles ON user_roles.roles_id = roles.id").
		Where("roles.id IN (1, 4)"). 
		Where("usuarios.activo = 0").
		Where("usuarios.idusuario = ?", therapistID).
		First(&therapist).Error

	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return errors.New("el usuario seleccionado no tiene rol de terapeuta o administrador")
		}
		return err
	}

	return nil
}

func (s *TherapyService) ExportToPDF(sessionID, userID uint, roles []string) ([]byte, string, error) {
	session, err := s.GetByID(sessionID, userID, roles)
	if err != nil {
		return nil, "", err
	}

	filename := fmt.Sprintf("sesion_%d_%s.pdf", session.ID, session.FechaSesion.Format("2006-01-02"))

	pdfContent := fmt.Sprintf(`
		SESIÓN TERAPÉUTICA - REPORTE

		ID: %d
		Paciente: %s
		Terapeuta: %s
		Fecha: %s
		Hora: %s - %s
		Duración: %d minutos

		Ubicación: %s
		Dirección: %s

		Descripción:
		%s

		Objetivos:
		%s

		Materiales:
		%s

		Notas del Terapeuta:
		%s

		Estado: %s
		Tipo: %s
		Modalidad: %s

		Generado el: %s
	`,
		session.ID,
		session.Paciente.NombresApellidos,
		session.Terapeuta.Nombres_Apellidos,
		session.FechaSesion.Format("02/01/2006"),
		session.HoraInicio,
		session.HoraFin,
		session.Duracion,
		session.Ubicacion,
		session.Direccion,
		session.Descripcion,
		strings.Join([]string(session.Objetivos), "\n• "),
		strings.Join([]string(session.Materiales), "\n• "),
		session.NotasTerapeuta,
		session.Estado,
		session.TipoSesion,
		session.Modalidad,
		time.Now().Format("02/01/2006 15:04:05"),
	)

	return []byte(pdfContent), filename, nil
}

func (s *TherapyService) ExportToJPG(sessionID, userID uint, roles []string) ([]byte, string, error) {
	session, err := s.GetByID(sessionID, userID, roles)
	if err != nil {
		return nil, "", err
	}

	filename := fmt.Sprintf("sesion_%d_%s.jpg", session.ID, session.FechaSesion.Format("2006-01-02"))

	jpgPlaceholder := []byte("JPG_PLACEHOLDER_DATA")

	return jpgPlaceholder, filename, nil
}

func getHourFromTimeString(timeStr string) int {
	parts := strings.Split(timeStr, ":")
	if len(parts) >= 1 {
		hour := 0
		fmt.Sscanf(parts[0], "%d", &hour)
		return hour
	}
	return 0
}

func getMinuteFromTimeString(timeStr string) int {
	parts := strings.Split(timeStr, ":")
	if len(parts) >= 2 {
		minute := 0
		fmt.Sscanf(parts[1], "%d", &minute)
		return minute
	}
	return 0
}

func (s *TherapyService) hasRole(roles []string, targetRole string) bool {
	for _, role := range roles {
		role = strings.TrimSpace(role)
		roleLower := strings.ToLower(role)
		targetLower := strings.ToLower(targetRole)

		if role == targetRole || roleLower == targetLower {
			return true
		}

		switch targetLower {
		case "tr", "terapeuta":
			if roleLower == "tr" || roleLower == "terapeuta" {
				return true
			}
		case "ad", "administrador", "admin":
			if roleLower == "ad" || roleLower == "administrador" || roleLower == "admin" {
				return true
			}
		case "pd", "padre", "cuidador":
			if roleLower == "pd" || roleLower == "padre" || roleLower == "cuidador" {
				return true
			}
		}
	}
	return false
}

func (s *TherapyService) GetPatientStats(patientID, userID uint, roles []string) (*dto.PatientStatsResponse, error) {

	var patient models.Patient
	patientQuery := s.db.Where("id = ? AND activo = ?", patientID, true)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		
		patientQuery = patientQuery.Where("cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {

		patientQuery = patientQuery.Joins("JOIN therapy_sessions ON therapy_sessions.paciente_id = patients.id").
			Where("therapy_sessions.terapeuta_id = ? AND therapy_sessions.is_deleted = ?", userID, false)
	}
	

	err := patientQuery.First(&patient).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errors.New("paciente no encontrado o sin acceso")
		}
		return nil, err
	}


	var sessions []models.TherapySession
	sessionQuery := s.db.Where("paciente_id = ? AND is_deleted = ?", patientID, false)


	if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		sessionQuery = sessionQuery.Where("terapeuta_id = ?", userID)
	}

	err = sessionQuery.Find(&sessions).Error
	if err != nil {
		return nil, err
	}


	totalSessions := len(sessions)
	completedSessions := 0
	scheduledSessions := 0
	cancelledSessions := 0
	totalDuration := 0
	var lastSessionDate *time.Time

	for _, session := range sessions {
		switch session.Estado {
		case "completada":
			completedSessions++
		case "programada":
			scheduledSessions++
		case "cancelada":
			cancelledSessions++
		}

		if session.Estado == "completada" {
			totalDuration += session.Duracion
		}

		if lastSessionDate == nil || session.FechaSesion.After(*lastSessionDate) {
			lastSessionDate = &session.FechaSesion
		}
	}


	progressPercentage := 0
	if totalSessions > 0 {
		progressPercentage = (completedSessions * 100) / totalSessions
	}


	daysSinceLastSession := 0
	lastSessionDateStr := ""
	if lastSessionDate != nil {
		daysSinceLastSession = int(time.Since(*lastSessionDate).Hours() / 24)
		lastSessionDateStr = lastSessionDate.Format("2006-01-02")
	}


	averageSessionDuration := 0
	if completedSessions > 0 {
		averageSessionDuration = totalDuration / completedSessions
	}

	return &dto.PatientStatsResponse{
		PatientID:              patientID,
		PatientName:            patient.NombresApellidos,
		TotalSessions:          totalSessions,
		CompletedSessions:      completedSessions,
		ScheduledSessions:      scheduledSessions,
		CancelledSessions:      cancelledSessions,
		ProgressPercentage:     progressPercentage,
		LastSessionDate:        lastSessionDateStr,
		DaysSinceLastSession:   daysSinceLastSession,
		AverageSessionDuration: averageSessionDuration,
	}, nil
}



func (s *TherapyService) CreateTherapistRating(dto *dto.CreateTherapistRatingDTO, userID uint, roles []string) (*models.TherapistRating, error) {
	log.Printf("🎯 CreateTherapistRating: userID=%d, roles=%v, dto.CaregiverID=%d", userID, roles, dto.CaregiverID)

	isAdmin := s.hasRole(roles, "AD")
	log.Printf("   👤 Usuario es admin: %v", isAdmin)


	var session models.TherapySession
	if err := s.db.Where("id = ? AND estado IN (?)", dto.SessionID, []string{"completada", "programada", "en_progreso"}).First(&session).Error; err != nil {
		log.Printf("❌ Sesión no encontrada: %d", dto.SessionID)
		return nil, errors.New("sesión no encontrada o no disponible para calificar")
	}
	log.Printf("✅ Sesión: ID=%d, PacienteID=%d, TerapeutaID=%d", session.ID, session.PacienteID, session.TerapeutaID)


	var patient models.Patient
	if err := s.db.Where("id = ?", session.PacienteID).First(&patient).Error; err != nil {
		log.Printf("❌ Paciente no encontrado: %d", session.PacienteID)
		return nil, errors.New("paciente de la sesión no encontrado")
	}
	log.Printf("✅ Paciente: ID=%d, CuidadorID=%v", patient.ID, patient.CuidadorID)


	if isAdmin {

		if patient.CuidadorID == nil {
			log.Printf("❌ Paciente sin cuidador asignado")
			return nil, errors.New("el paciente no tiene cuidador asignado")
		}
		log.Printf("✅ ADMIN autorizado: calificando en nombre de cuidador %d (paciente tiene cuidador %d)",
			dto.CaregiverID, *patient.CuidadorID)
	} else {

		if patient.CuidadorID == nil {
			log.Printf("❌ Paciente sin cuidador asignado")
			return nil, errors.New("no tienes permisos para calificar esta sesión - paciente sin cuidador")
		}
		if *patient.CuidadorID != userID {
			log.Printf("❌ Cuidador no autorizado: esperado=%d, tú=%d", *patient.CuidadorID, userID)
			return nil, errors.New("no tienes permisos para calificar esta sesión - paciente no asignado a ti")
		}
		log.Printf("✅ Cuidador autorizado: ID=%d", userID)
	}


	var existingRating models.TherapistRating
	if err := s.db.Where("session_id = ? AND caregiver_id = ?", dto.SessionID, dto.CaregiverID).First(&existingRating).Error; err == nil {
		return nil, errors.New("ya has calificado esta sesión")
	}


	rating := models.TherapistRating{
		SessionID:   dto.SessionID,
		TherapistID: session.TerapeutaID,  
		CaregiverID: dto.CaregiverID,     
		PatientID:   session.PacienteID,  
		Rating:      dto.Rating,
		Comment:     dto.Comment,
	}

	if err := s.db.Create(&rating).Error; err != nil {
		return nil, fmt.Errorf("error al crear la calificación: %v", err)
	}


	if dto.Rating <= 3 {
		log.Printf("⚠️  Low rating detected (%d stars) for therapist %d by user %d", dto.Rating, session.TerapeutaID, userID)

	
		if err := s.updateTherapistDisqualification(session.TerapeutaID); err != nil {
			log.Printf("❌ Error updating therapist disqualification: %v", err)
		}


		newTherapistID, err := s.reassignTherapistForPatient(session.PacienteID, session.TerapeutaID)
		if err != nil {
			log.Printf("❌ Error reassigning therapist: %v", err)
		} else if newTherapistID > 0 {
			// Guardar el ID del nuevo terapeuta en la sesión que fue calificada
			log.Printf("💾 Guardando terapeuta reasignado (ID: %d) en sesión %d", newTherapistID, dto.SessionID)
			if err := s.db.Model(&models.TherapySession{}).
				Where("id = ?", dto.SessionID).
				Update("terapeuta_reasignado_id", newTherapistID).Error; err != nil {
				log.Printf("❌ Error guardando terapeuta reasignado: %v", err)
			} else {
				log.Printf("✅ Terapeuta reasignado guardado correctamente en sesión %d", dto.SessionID)
			}
		}
	}

	if err := s.db.
		Preload("Therapist", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Caregiver", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Patient").
		First(&rating, rating.ID).Error; err != nil {
		log.Printf("Warning: Could not preload rating relationships: %v", err)
	}

	return &rating, nil
}

func (s *TherapyService) GetSessionRating(sessionID uint, userID uint, userRoles []string) (*models.TherapistRating, error) {
	var rating models.TherapistRating

	query := s.db.
		Preload("Therapist", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Caregiver", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Patient").
		Where("session_id = ?", sessionID)

	hasAdminRole := false
	for _, role := range userRoles {
		if role == "AD" || role == "admin" || role == "administrador" {
			hasAdminRole = true
			break
		}
	}

	if !hasAdminRole {
		// Non-admin users can only see ratings where they are the caregiver
		query = query.Where("caregiver_id = ?", userID)
	}

	if err := query.First(&rating).Error; err != nil {
		return nil, errors.New("calificación no encontrada")
	}

	return &rating, nil
}

func (s *TherapyService) updateTherapistDisqualification(therapistID uint) error {
	var disqualification models.TherapistDisqualification


	result := s.db.Where("therapist_id = ?", therapistID).First(&disqualification)
	if result.Error != nil {
		if errors.Is(result.Error, gorm.ErrRecordNotFound) {

			disqualification = models.TherapistDisqualification{
				TherapistID: therapistID,
				BadRatings:  1,
			}
			if err := s.db.Create(&disqualification).Error; err != nil {
				return fmt.Errorf("error creating disqualification record: %v", err)
			}
		} else {
			return fmt.Errorf("error finding disqualification record: %v", result.Error)
		}
	} else {
		// Update existing record
		disqualification.BadRatings++
		if err := s.db.Save(&disqualification).Error; err != nil {
			return fmt.Errorf("error updating disqualification count: %v", err)
		}
	}

	log.Printf("Therapist %d now has %d bad ratings", therapistID, disqualification.BadRatings)

	if disqualification.BadRatings >= 20 {
		log.Printf("Therapist %d has reached 20 bad ratings - deleting account", therapistID)
		if err := s.deleteTherapistAccount(therapistID); err != nil {
			return fmt.Errorf("error deleting therapist account: %v", err)
		}

		disqualification.IsDeleted = true
		s.db.Save(&disqualification)
	}

	return nil
}

func (s *TherapyService) reassignTherapistForPatient(patientID, oldTherapistID uint) (uint, error) {
	log.Printf("🔄 Buscando terapeuta de reemplazo para paciente %d (excluir terapeuta %d)", patientID, oldTherapistID)
	var disqualifiedIDs []uint
	s.db.Table("therapist_disqualifications").
		Where("is_deleted = ?", true).
		Pluck("therapist_id", &disqualifiedIDs)

	log.Printf("   📋 Terapeutas descalificados: %v", disqualifiedIDs)

	var newTherapist models.Usuarios
	query := s.db.Table("usuarios").
		Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
		Joins("JOIN roles ON user_roles.roles_id = roles.id").
		Where("roles.id = 4"). 
		Where("usuarios.idusuario != ?", oldTherapistID).
		Where("usuarios.activo = 0") 
	if len(disqualifiedIDs) > 0 {
		query = query.Where("usuarios.idusuario NOT IN (?)", disqualifiedIDs)
	}

	query = query.Having("(SELECT COUNT(*) FROM patients WHERE terapeuta_id = usuarios.idusuario) < 20")

	err := query.First(&newTherapist).Error
	if err != nil {
		log.Printf("❌ No hay terapeutas alternativos disponibles para paciente %d", patientID)
		return 0, nil 
	}

	log.Printf("✅ Nuevo terapeuta encontrado: %s (ID: %d)", newTherapist.Nombres_Apellidos, newTherapist.ID)

	if err := s.db.Model(&models.Patient{}).Where("id = ?", patientID).Update("terapeuta_id", newTherapist.ID).Error; err != nil {
		log.Printf("⚠️  Warning: Could not update patient's default therapist: %v", err)
	}

	result := s.db.Model(&models.TherapySession{}).
		Where("paciente_id = ? AND terapeuta_id = ? AND estado = ?", patientID, oldTherapistID, "programada").
		Update("terapeuta_id", newTherapist.ID)

	if result.Error != nil {
		return 0, fmt.Errorf("error reassigning therapist: %v", result.Error)
	}

	log.Printf("✅ Reasignadas %d sesiones futuras del terapeuta %d al terapeuta %d para paciente %d",
		result.RowsAffected, oldTherapistID, newTherapist.ID, patientID)
	if result.RowsAffected == 0 {
		log.Printf("📅 No hay sesiones futuras. Creando nueva sesión automática con el nuevo terapeuta...")
		if err := s.createAutomaticFollowUpSession(patientID, newTherapist.ID, oldTherapistID); err != nil {
			log.Printf("⚠️  Warning: No se pudo crear sesión automática: %v", err)
			
		}
	}

	return newTherapist.ID, nil
}

func (s *TherapyService) createAutomaticFollowUpSession(patientID, newTherapistID, oldTherapistID uint) error {

	var patient models.Patient
	if err := s.db.Preload("Cuidador").First(&patient, patientID).Error; err != nil {
		return fmt.Errorf("no se pudo cargar paciente: %v", err)
	}
	now := time.Now()
	sessionDate := s.calculateBusinessDays(now, 8)

	horaInicio := "10:00"
	horaFin := "11:00"

	log.Printf("📅 Creando sesión de seguimiento para %s con nuevo terapeuta %d", patient.NombresApellidos, newTherapistID)
	log.Printf("   Fecha programada: %s, Hora: %s - %s", sessionDate.Format("2006-01-02"), horaInicio, horaFin)


	newSession := models.TherapySession{
		PacienteID:     patientID,
		TerapeutaID:    newTherapistID,
		FechaSesion:    sessionDate,
		HoraInicio:     horaInicio,
		HoraFin:        horaFin,
		Duracion:       60,
		Ubicacion:      "Consultorio principal",
		Direccion:      "Por confirmar",
		Descripcion:    fmt.Sprintf("Sesión de seguimiento con nuevo terapeuta. Sesión anterior con terapeuta anterior (ID: %d) fue calificada con baja puntuación.", oldTherapistID),
		Objetivos:      models.StringArray{"Evaluación inicial con nuevo terapeuta", "Establecer rapport", "Definir plan de tratamiento"},
		Materiales:     models.StringArray{"Material estándar de terapia"},
		TipoSesion:     "individual",
		Modalidad:      "presencial",
		Estado:         "programada",
		NotasTerapeuta: "Sesión creada automáticamente por cambio de terapeuta debido a baja calificación",
	}

	if err := s.db.Create(&newSession).Error; err != nil {
		return fmt.Errorf("error creando sesión automática: %v", err)
	}

	
	if patient.Cuidador != nil {
		s.sendNewTherapistNotification(&newSession, &patient, patient.Cuidador, newTherapistID, oldTherapistID)
	}

	return nil
}

func (s *TherapyService) calculateBusinessDays(startDate time.Time, businessDays int) time.Time {
	currentDate := startDate
	daysAdded := 0

	for daysAdded < businessDays {
		currentDate = currentDate.AddDate(0, 0, 1)

		weekday := currentDate.Weekday()
		if weekday != time.Saturday && weekday != time.Sunday {
			daysAdded++
		}
	}

	return currentDate
}

func (s *TherapyService) sendNewTherapistNotification(session *models.TherapySession, patient *models.Patient, cuidador *models.Usuarios, newTherapistID, oldTherapistID uint) {

	var newTherapist models.Usuarios
	if err := s.db.First(&newTherapist, newTherapistID).Error; err != nil {
		
		return
	}
}

func (s *TherapyService) deleteTherapistAccount(therapistID uint) error {
	if err := s.db.Model(&models.Usuarios{}).Where("id = ?", therapistID).Update("estado_cuenta", "eliminada").Error; err != nil {
		return fmt.Errorf("error deleting therapist account: %v", err)
	}
	if err := s.db.Model(&models.TherapySession{}).
		Where("terapeuta_id = ? AND estado = ?", therapistID, "programada").
		Update("estado", "cancelada").Error; err != nil {
		log.Printf("Warning: Could not cancel future sessions for deleted therapist %d: %v", therapistID, err)
	}

	log.Printf("Successfully deleted therapist account %d due to excessive bad ratings", therapistID)
	return nil
}

func (s *TherapyService) GetTherapistRatings(therapistID uint, userID uint, roles []string) ([]dto.TherapistRatingResponse, error) {

	isAdmin := s.hasRole(roles, "AD")
	isTherapist := s.hasRole(roles, "TR") && userID == therapistID

	if !isAdmin && !isTherapist {
		return nil, errors.New("no tienes permisos para ver estas calificaciones")
	}

	var ratings []models.TherapistRating
	if err := s.db.Where("therapist_id = ?", therapistID).
		Preload("Therapist", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Caregiver", func(db *gorm.DB) *gorm.DB {
			return db.Select("idusuario", "nombres_apellidos", "correo", "telefono", "foto_movil")
		}).
		Preload("Patient").
		Order("created_at DESC").
		Find(&ratings).Error; err != nil {
		return nil, fmt.Errorf("error al obtener calificaciones: %v", err)
	}

	responses := make([]dto.TherapistRatingResponse, len(ratings))
	for i, rating := range ratings {
		responses[i] = dto.TherapistRatingResponse{
			ID:          rating.ID,
			SessionID:   rating.SessionID,
			TherapistID: rating.TherapistID,
			CaregiverID: rating.CaregiverID,
			PatientID:   rating.PatientID,
			Rating:      rating.Rating,
			Comment:     rating.Comment,
			CreatedAt:   rating.CreatedAt.Format("2006-01-02 15:04:05"),
			UpdatedAt:   rating.UpdatedAt.Format("2006-01-02 15:04:05"),
		}

		if rating.Therapist.ID > 0 {
			responses[i].TherapistName = rating.Therapist.Nombres_Apellidos
		}
		if rating.Caregiver.ID > 0 {
			responses[i].CaregiverName = rating.Caregiver.Nombres_Apellidos
		}
		if rating.Patient.ID > 0 {
			responses[i].PatientName = rating.Patient.NombresApellidos
		}
	}

	return responses, nil
}

func (s *TherapyService) UpdateExpiredSessions() error {
	log.Printf("🔄 Iniciando actualización automática de estados de sesiones...")

	now := time.Now()

	var sessionsToUpdate []models.TherapySession

	if err := s.db.Preload("Paciente").Preload("Terapeuta").
		Where("estado = ? AND fecha_sesion < ? AND is_deleted = ?",
			"programada",
			now.Add(-2*time.Hour),
			false).
		Find(&sessionsToUpdate).Error; err != nil {
		log.Printf("❌ Error al obtener sesiones para actualizar: %v", err)
		return err
	}

	if len(sessionsToUpdate) == 0 {
		log.Printf("✅ No hay sesiones para actualizar automáticamente")
		return nil
	}

	log.Printf("📋 Encontradas %d sesiones para actualizar", len(sessionsToUpdate))

	updatedCount := 0
	for _, session := range sessionsToUpdate {
		if err := s.db.Model(&session).Updates(map[string]interface{}{
			"estado":     "completada",
			"updated_at": now,
		}).Error; err != nil {
			log.Printf("❌ Error al actualizar sesión ID %d: %v", session.ID, err)
			continue
		}

		updatedCount++
		log.Printf("✅ Sesión ID %d actualizada automáticamente a 'completada' - Paciente: %s, Fecha: %s",
			session.ID,
			session.Paciente.NombresApellidos,
			session.FechaSesion.Format("2006-01-02 15:04"))
		go s.sendAutomaticCompletionNotification(&session)
	}

	log.Printf("🎯 Actualización automática completada: %d/%d sesiones actualizadas", updatedCount, len(sessionsToUpdate))

	return nil
}


func (s *TherapyService) sendAutomaticCompletionNotification(session *models.TherapySession) {
	if session.Paciente.CuidadorID != nil {
		var cuidador models.Usuarios
		if err := s.db.First(&cuidador, *session.Paciente.CuidadorID).Error; err == nil {
			
			s.emailService.SendSessionAutoCompletedEmail(session, &session.Paciente, &cuidador)
		}
	}
}


func (s *TherapyService) StartAutomaticSessionUpdater() {
	log.Printf("🚀 Iniciando actualizador automático de sesiones (cada hora)...")
	go func() {
		if err := s.UpdateExpiredSessions(); err != nil {
			log.Printf("❌ Error en actualización automática inicial: %v", err)
		}
	}()
	ticker := time.NewTicker(1 * time.Hour)
	go func() {
		for {
			select {
			case <-ticker.C:
				if err := s.UpdateExpiredSessions(); err != nil {
					log.Printf("❌ Error en actualización automática programada: %v", err)
				}
			}
		}
	}()

	log.Printf("✅ Actualizador automático de sesiones iniciado correctamente")
}

func (s *TherapyService) GetSessionsRequiringUpdate() ([]models.TherapySession, error) {
	var sessions []models.TherapySession
	now := time.Now()

	if err := s.db.Preload("Paciente").Preload("Terapeuta").
		Where("estado = ? AND fecha_sesion < ? AND is_deleted = ?",
			"programada",
			now.Add(-2*time.Hour),
			false).
		Find(&sessions).Error; err != nil {
		return nil, err
	}

	return sessions, nil
}


func (s *TherapyService) ValidatePatientAccess(patientID, userID uint, roles string) (bool, error) {
	roleSlice := strings.Split(roles, ",")


	if s.hasRole(roleSlice, "AD") {
		return true, nil
	}

	var patient models.Patient
	if err := s.db.Where("id = ?", patientID).First(&patient).Error; err != nil {
		return false, errors.New("patient not found")
	}


	if s.hasRole(roleSlice, "TR") {
		if patient.TerapeutaID == userID {
			return true, nil
		}
	}

	if s.hasRole(roleSlice, "PD") {
		if patient.CuidadorID != nil && *patient.CuidadorID == userID {
			return true, nil
		}
	}

	return false, nil
}


func (s *TherapyService) GetPatientReportHistory(patientID uint, page, limit int, reportType string) ([]models.ReportHistoryResponse, int, error) {
	var reports []models.ReportHistory
	var total int64
	query := s.db.Model(&models.ReportHistory{}).
		Where("paciente_id = ?", patientID)

	if reportType != "" {
		query = query.Where("report_type = ?", reportType)
	}


	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}


	offset := (page - 1) * limit
	if err := query.
		Preload("GeneratedByUser").
		Preload("CurrentTherapist").
		Preload("PreviousTherapist").
		Order("created_at DESC").
		Offset(offset).
		Limit(limit).
		Find(&reports).Error; err != nil {
		return nil, 0, err
	}


	var responses []models.ReportHistoryResponse
	for _, report := range reports {
		response := models.ReportHistoryResponse{
			ID:                report.ID,
			ReportType:        report.ReportType,
			ReportTitle:       report.ReportTitle,
			ReportDescription: report.ReportDescription,
			DateRange:         report.DateRange,
			TotalSessions:     report.TotalSessions,
			CompletedSessions: report.CompletedSessions,
			AverageConfidence: report.AverageConfidence,
			DominantEmotion:   report.DominantEmotion,
			EmotionTrend:      report.EmotionTrend,
			TherapistChanged:  report.TherapistChanged,
			FileFormat:        report.FileFormat,
			FileSizeBytes:     report.FileSizeBytes,
			CreatedAt:         report.CreatedAt,
			GeneratedByUser: models.UserBasicInfo{
				ID:               report.GeneratedByUser.ID,
				NombresApellidos: report.GeneratedByUser.Nombres_Apellidos,
				Correo:          report.GeneratedByUser.Correo,
				Telefono:        report.GeneratedByUser.Telefono,
			},
			CurrentTherapist: models.UserBasicInfo{
				ID:               report.CurrentTherapist.ID,
				NombresApellidos: report.CurrentTherapist.Nombres_Apellidos,
				Correo:          report.CurrentTherapist.Correo,
				Telefono:        report.CurrentTherapist.Telefono,
			},
		}

		if report.PreviousTherapist != nil {
			response.PreviousTherapist = &models.UserBasicInfo{
				ID:               report.PreviousTherapist.ID,
				NombresApellidos: report.PreviousTherapist.Nombres_Apellidos,
				Correo:          report.PreviousTherapist.Correo,
				Telefono:        report.PreviousTherapist.Telefono,
			}
		}

		responses = append(responses, response)
	}

	return responses, int(total), nil
}


func (s *TherapyService) GeneratePatientFullReport(patientID, userID uint, dateFrom, dateTo *time.Time) (*models.PatientReportSummary, error) {
	log.Printf("🔄 Generando reporte completo para paciente %d", patientID)

	var patient models.Patient
	if err := s.db.Preload("Cuidador").
		Where("id = ?", patientID).
		First(&patient).Error; err != nil {
		return nil, errors.New("patient not found")
	}

	// Primero intentar obtener datos reales de la tabla emociones_detectadas
	emotionHistory := s.getEmotionHistoryFromDatabase(patientID, dateFrom, dateTo)
	if emotionHistory != nil {
		log.Printf("✅ Obtenidos %d análisis emocionales de la base de datos local", len(emotionHistory.Analyses))
	} else {
		// Si no hay datos locales, intentar ML service
		jwtToken, err := s.generateMLServiceToken(userID)
		if err != nil {
			log.Printf("⚠️ Error generando token para ML service: %v. Continuando sin autenticación.", err)
		} else {
			s.mlClient.SetJWTToken(jwtToken)
		}

		emotionHistory, err = s.mlClient.GetPatientEmotionHistory(int(patientID), dateFrom, dateTo, 500)
		if err != nil {
			log.Printf("⚠️ Error obteniendo historial emocional del ML service: %v. Usando datos simulados.", err)
			return s.generatePatientFullReportFallback(patientID, userID, dateFrom, dateTo)
		}
	}

	log.Printf("✅ Obtenidos %d análisis emocionales del ML service", len(emotionHistory.Analyses))

	// Obtener sesiones del backend
	sessionQuery := s.db.Model(&models.TherapySession{}).
		Where("paciente_id = ? AND is_deleted = ?", patientID, false)

	if dateFrom != nil {
		sessionQuery = sessionQuery.Where("fecha_sesion >= ?", *dateFrom)
	}
	if dateTo != nil {
		sessionQuery = sessionQuery.Where("fecha_sesion <= ?", *dateTo)
	}

	var sessions []models.TherapySession
	if err := sessionQuery.
		Preload("Terapeuta").
		Preload("TerapeutaReasignado").
		Order("fecha_sesion ASC").
		Find(&sessions).Error; err != nil {
		return nil, err
	}

	if len(sessions) == 0 {
		return nil, errors.New("no sessions found")
	}

	log.Printf("✅ Encontradas %d sesiones para el reporte", len(sessions))


	emotionBySession := make(map[int]*utils.EmotionAnalysis)
	for _, analysis := range emotionHistory.Analyses {
		if analysis.TherapySessionID != nil {
			emotionBySession[*analysis.TherapySessionID] = &analysis
		}
	}


	var sessionWithAnalysis []models.SessionWithAnalysis
	for _, session := range sessions {
		sessionDetail := models.TherapySessionDetail{
			ID:              session.ID,
			FechaSesion:     session.FechaSesion,
			HoraInicio:      session.HoraInicio,
			HoraFin:         session.HoraFin,
			Duracion:        session.Duracion,
			Ubicacion:       session.Ubicacion,
			Direccion:       session.Direccion,
			Descripcion:     session.Descripcion,
			Objetivos:       []string(session.Objetivos),
			Materiales:      []string(session.Materiales),
			Estado:          session.Estado,
			TipoSesion:      session.TipoSesion,
			Modalidad:       session.Modalidad,
			Terapeuta: models.UserBasicInfo{
				ID:               session.Terapeuta.ID,
				NombresApellidos: session.Terapeuta.Nombres_Apellidos,
				Correo:          session.Terapeuta.Correo,
				Telefono:        session.Terapeuta.Telefono,
			},
		}

		if session.TerapeutaReasignado != nil {
			sessionDetail.TerapeutaReasignado = &models.UserBasicInfo{
				ID:               session.TerapeutaReasignado.ID,
				NombresApellidos: session.TerapeutaReasignado.Nombres_Apellidos,
				Correo:          session.TerapeutaReasignado.Correo,
				Telefono:        session.TerapeutaReasignado.Telefono,
			}
		}


		var emotionAnalysis *models.EmotionResult
		if mlAnalysis, exists := emotionBySession[int(session.ID)]; exists {

			spanishEmotion := s.translateEmotionToSpanish(mlAnalysis.EmotionName)
			emotionAnalysis = &models.EmotionResult{
				ID:                   uint(mlAnalysis.ID),
				SessionID:            session.ID,
				EmotionType:          spanishEmotion,
				ConfidencePercentage: mlAnalysis.Confidence,
				ImageAnalyzed:        true,
				AnalysisDate:         mlAnalysis.SessionDate,
				Notes:                fmt.Sprintf("Análisis ML - %s (%.1f%% confianza, calidad: %s)",
					spanishEmotion, mlAnalysis.Confidence, mlAnalysis.QualityStatus),
			}
		}

		sessionWithAnalysis = append(sessionWithAnalysis, models.SessionWithAnalysis{
			Session:         sessionDetail,
			EmotionAnalysis: emotionAnalysis,
			TherapistNotes:  session.NotasTerapeuta,
		})
	}

	
	emotionSummary := s.calculateEmotionSummaryFromMLData(emotionHistory)
	therapistHistory := s.getTherapistHistory(patientID)

	reportPeriod := emotionHistory.DateRange
	if reportPeriod == "" {
		reportPeriod = "Historial completo"
	}

	var generatedByUser models.Usuarios
	s.db.Where("id = ?", userID).First(&generatedByUser)

	report := &models.PatientReportSummary{
		Patient: models.PatientDetailInfo{
			ID:                 patient.ID,
			SerialID:           patient.SerialID,
			NombresApellidos:   patient.NombresApellidos,
			FechaNacimiento:    time.Time(patient.FechaNacimiento),
			Edad:               s.calculateAge(time.Time(patient.FechaNacimiento)),
			Genero:             patient.Sexo,
			TipoDocumento:      patient.TipoDocumento,
			NumDocumento:       patient.NumDocumento,
			Telefono:           "",
			Direccion:          "",
			CondicionMedica:    patient.DiagnosticoClinico,
			Medicamentos:       []string{},
			Alergias:           []string{},
			ContactoEmergencia: "",
			CreatedAt:          patient.CreatedAt,
		},
		Caregiver: models.UserBasicInfo{
			ID:               patient.Cuidador.ID,
			NombresApellidos: patient.Cuidador.Nombres_Apellidos,
			Correo:          patient.Cuidador.Correo,
			Telefono:        patient.Cuidador.Telefono,
		},
		Sessions:         sessionWithAnalysis,
		EmotionSummary:   emotionSummary,
		TherapistHistory: therapistHistory,
		GeneratedAt:      time.Now(),
		GeneratedBy: models.UserBasicInfo{
			ID:               generatedByUser.ID,
			NombresApellidos: generatedByUser.Nombres_Apellidos,
			Correo:          generatedByUser.Correo,
			Telefono:        generatedByUser.Telefono,
		},
		ReportPeriod: reportPeriod,
	}


	go s.saveReportHistory(patientID, userID, report, "patient_summary", "PDF")

	log.Printf("✅ Reporte generado exitosamente para paciente %d con %d análisis emocionales reales",
		patientID, len(emotionHistory.Analyses))

	return report, nil
}


func (s *TherapyService) generateMLServiceToken(userID uint) (string, error) {
	var user models.Usuarios
	if err := s.db.Preload("Roles").Where("id = ?", userID).First(&user).Error; err != nil {
		return "", err
	}
	token, _, err := utils.GenerateToken(&user)
	return token, err
}


func (s *TherapyService) translateEmotionToSpanish(emotion string) string {
	translations := map[string]string{
		"happy":     "Feliz",
		"sad":       "Triste",
		"angry":     "Enojado",
		"fear":      "Miedo",
		"surprise":  "Sorpresa",
		"disgust":   "Disgusto",
		"neutral":   "Neutral",
		"joy":       "Alegría",
		"sadness":   "Tristeza",
		"anger":     "Ira",
		"fearful":   "Temeroso",
		"surprised": "Sorprendido",
		"disgusted": "Disgustado",
		"calm":      "Tranquilo",
		"excited":   "Emocionado",
		"anxious":   "Ansioso",
		"confused":  "Confundido",
		"bored":     "Aburrido",
		"focused":   "Concentrado",
	}

	if spanish, exists := translations[strings.ToLower(emotion)]; exists {
		return spanish
	}
	return emotion 
}


func (s *TherapyService) calculateEmotionSummaryFromMLData(mlHistory *utils.PatientEmotionHistory) models.EmotionAnalysisSummary {
	if mlHistory.TotalAnalyses == 0 {
		return models.EmotionAnalysisSummary{
			TotalAnalyses:     0,
			AverageConfidence: 0,
			DominantEmotion:   "Sin datos",
			EmotionDistribution: make(map[string]int),
			ConfidenceTrend:   "Sin tendencia",
			EmotionTrend:      "Sin tendencia",
		}
	}

	var recentEmotions []models.EmotionResult
	var highestConfidence, lowestConfidence float64

	if len(mlHistory.Analyses) > 0 {
		highestConfidence = mlHistory.Analyses[0].Confidence
		lowestConfidence = mlHistory.Analyses[0].Confidence

		recentCount := 3
		if len(mlHistory.Analyses) < recentCount {
			recentCount = len(mlHistory.Analyses)
		}

		for i := len(mlHistory.Analyses) - recentCount; i < len(mlHistory.Analyses); i++ {
			analysis := mlHistory.Analyses[i]
			sessionID := uint(0)
			if analysis.TherapySessionID != nil {
				sessionID = uint(*analysis.TherapySessionID)
			}
			
			recentEmotions = append(recentEmotions, models.EmotionResult{
				ID:                   uint(analysis.ID),
				SessionID:            sessionID,
				EmotionType:          analysis.EmotionName,
				ConfidencePercentage: analysis.Confidence,
				ImageAnalyzed:        true,
				AnalysisDate:         analysis.SessionDate,
				Notes:                fmt.Sprintf("Análisis reciente - %s con %.1f%% confianza", analysis.EmotionName, analysis.Confidence),
			})

			if analysis.Confidence > highestConfidence {
				highestConfidence = analysis.Confidence
			}
			if analysis.Confidence < lowestConfidence {
				lowestConfidence = analysis.Confidence
			}
		}
	}


	dominantEmotionSpanish := s.translateEmotionToSpanish(mlHistory.MostFrequentEmotion)

	return models.EmotionAnalysisSummary{
		TotalAnalyses:       mlHistory.TotalAnalyses,
		AverageConfidence:   mlHistory.AverageConfidence,
		DominantEmotion:     dominantEmotionSpanish,
		EmotionDistribution: mlHistory.EmotionDistribution,
		ConfidenceTrend:     mlHistory.ConfidenceTrend,
		EmotionTrend:        "Estable",
		HighestConfidence:   highestConfidence,
		LowestConfidence:    lowestConfidence,
		RecentEmotions:      recentEmotions,
	}
}


func (s *TherapyService) generatePatientFullReportFallback(patientID, userID uint, dateFrom, dateTo *time.Time) (*models.PatientReportSummary, error) {
	log.Printf("⚠️ Generando reporte con datos simulados para paciente %d", patientID)
	
	var patient models.Patient
	if err := s.db.Preload("Cuidador").Where("id = ?", patientID).First(&patient).Error; err != nil {
		return nil, errors.New("patient not found")
	}

	sessionQuery := s.db.Model(&models.TherapySession{}).
		Where("paciente_id = ? AND is_deleted = ?", patientID, false)

	if dateFrom != nil {
		sessionQuery = sessionQuery.Where("fecha_sesion >= ?", *dateFrom)
	}
	if dateTo != nil {
		sessionQuery = sessionQuery.Where("fecha_sesion <= ?", *dateTo)
	}

	var sessions []models.TherapySession
	if err := sessionQuery.Preload("Terapeuta").Preload("TerapeutaReasignado").Order("fecha_sesion ASC").Find(&sessions).Error; err != nil {
		return nil, err
	}

	if len(sessions) == 0 {
		return nil, errors.New("no sessions found")
	}

	var emotionAnalyses []models.EmotionResult
	var sessionWithAnalysis []models.SessionWithAnalysis

	for _, session := range sessions {
		sessionDetail := models.TherapySessionDetail{
			ID:              session.ID,
			FechaSesion:     session.FechaSesion,
			HoraInicio:      session.HoraInicio,
			HoraFin:         session.HoraFin,
			Duracion:        session.Duracion,
			Ubicacion:       session.Ubicacion,
			Direccion:       session.Direccion,
			Descripcion:     session.Descripcion,
			Objetivos:       []string(session.Objetivos),
			Materiales:      []string(session.Materiales),
			Estado:          session.Estado,
			TipoSesion:      session.TipoSesion,
			Modalidad:       session.Modalidad,
			Terapeuta: models.UserBasicInfo{
				ID:               session.Terapeuta.ID,
				NombresApellidos: session.Terapeuta.Nombres_Apellidos,
				Correo:          session.Terapeuta.Correo,
				Telefono:        session.Terapeuta.Telefono,
			},
		}

		if session.TerapeutaReasignado != nil {
			sessionDetail.TerapeutaReasignado = &models.UserBasicInfo{
				ID:               session.TerapeutaReasignado.ID,
				NombresApellidos: session.TerapeutaReasignado.Nombres_Apellidos,
				Correo:          session.TerapeutaReasignado.Correo,
				Telefono:        session.TerapeutaReasignado.Telefono,
			}
		}

		var emotionAnalysis *models.EmotionResult
		if session.Estado == "completada" {
			emotions := []string{"happy", "sad", "angry", "neutral", "surprised", "fearful", "disgust"}
			randomEmotion := emotions[session.ID%uint(len(emotions))]
			confidence := 75.0 + float64(session.ID%20)

			emotionAnalysis = &models.EmotionResult{
				ID:                   session.ID + 1000,
				SessionID:            session.ID,
				EmotionType:          randomEmotion,
				ConfidencePercentage: confidence,
				ImageAnalyzed:        true,
				AnalysisDate:         session.FechaSesion,
				Notes:                fmt.Sprintf("Análisis simulado - %s detectado con %.1f%% de confianza", randomEmotion, confidence),
			}
			emotionAnalyses = append(emotionAnalyses, *emotionAnalysis)
		}

		sessionWithAnalysis = append(sessionWithAnalysis, models.SessionWithAnalysis{
			Session:         sessionDetail,
			EmotionAnalysis: emotionAnalysis,
			TherapistNotes:  session.NotasTerapeuta,
		})
	}

	emotionSummary := s.calculateEmotionSummary(nil)
	therapistHistory := s.getTherapistHistory(patientID)

	reportPeriod := "Historial completo (datos simulados)"
	if dateFrom != nil && dateTo != nil {
		reportPeriod = fmt.Sprintf("Del %s al %s (datos simulados)",
			dateFrom.Format("02/01/2006"), dateTo.Format("02/01/2006"))
	}

	var generatedByUser models.Usuarios
	s.db.Where("id = ?", userID).First(&generatedByUser)

	report := &models.PatientReportSummary{
		Patient: models.PatientDetailInfo{
			ID:                 patient.ID,
			SerialID:           patient.SerialID,
			NombresApellidos:   patient.NombresApellidos,
			FechaNacimiento:    time.Time(patient.FechaNacimiento),
			Edad:               s.calculateAge(time.Time(patient.FechaNacimiento)),
			Genero:             patient.Sexo,
			TipoDocumento:      patient.TipoDocumento,
			NumDocumento:       patient.NumDocumento,
			Telefono:           "",
			Direccion:          "",
			CondicionMedica:    patient.DiagnosticoClinico,
			Medicamentos:       []string{},
			Alergias:           []string{},
			ContactoEmergencia: "",
			CreatedAt:          patient.CreatedAt,
		},
		Caregiver: models.UserBasicInfo{
			ID:               patient.Cuidador.ID,
			NombresApellidos: patient.Cuidador.Nombres_Apellidos,
			Correo:          patient.Cuidador.Correo,
			Telefono:        patient.Cuidador.Telefono,
		},
		Sessions:         sessionWithAnalysis,
		EmotionSummary:   emotionSummary,
		TherapistHistory: therapistHistory,
		GeneratedAt:      time.Now(),
		GeneratedBy: models.UserBasicInfo{
			ID:               generatedByUser.ID,
			NombresApellidos: generatedByUser.Nombres_Apellidos,
			Correo:          generatedByUser.Correo,
			Telefono:        generatedByUser.Telefono,
		},
		ReportPeriod: reportPeriod,
	}

	go s.saveReportHistory(patientID, userID, report, "patient_summary", "PDF")
	return report, nil
}

// DeletePatientReport elimina un reporte del historial
func (s *TherapyService) DeletePatientReport(patientID, reportID, userID uint, roles string) error {
	roleSlice := strings.Split(roles, ",")

	var report models.ReportHistory
	if err := s.db.Where("id = ? AND paciente_id = ?", reportID, patientID).First(&report).Error; err != nil {
		return errors.New("report not found")
	}

	if !s.hasRole(roleSlice, "AD") && report.GeneratedByUserID != userID {
		return errors.New("access denied")
	}

	return s.db.Delete(&report).Error
}


func (s *TherapyService) calculateAge(birthDate time.Time) int {
	now := time.Now()
	age := now.Year() - birthDate.Year()
	if now.YearDay() < birthDate.YearDay() {
		age--
	}
	return age
}


func (s *TherapyService) getTherapistHistory(patientID uint) []models.TherapistAssignment {
	var assignments []models.TherapistAssignment


	return assignments
}


func (s *TherapyService) calculateEmotionSummary(mlHistory *utils.PatientEmotionHistory) models.EmotionAnalysisSummary {
	if mlHistory == nil {
		return models.EmotionAnalysisSummary{
			TotalAnalyses:     0,
			AverageConfidence: 0,
			DominantEmotion:   "Sin datos",
			EmotionDistribution: make(map[string]int),
			ConfidenceTrend:   "Sin tendencia",
			EmotionTrend:      "Sin tendencia",
			HighestConfidence: 0,
			LowestConfidence:  0,
			RecentEmotions:    []models.EmotionResult{},
		}
	}

	return models.EmotionAnalysisSummary{
		TotalAnalyses:       mlHistory.TotalAnalyses,
		AverageConfidence:   mlHistory.AverageConfidence,
		DominantEmotion:     mlHistory.MostFrequentEmotion,
		EmotionDistribution: mlHistory.EmotionDistribution,
		ConfidenceTrend:     mlHistory.ConfidenceTrend,
		EmotionTrend:        "Estable", 
		HighestConfidence:   mlHistory.AverageConfidence,
		LowestConfidence:    mlHistory.AverageConfidence,
		RecentEmotions:      []models.EmotionResult{},
	}
}



func (s *TherapyService) saveReportHistory(patientID, userID uint, report *models.PatientReportSummary, reportType, format string) {

	reportHistory := models.ReportHistory{
		PacienteID:          patientID,
		GeneratedByUserID:   userID,
		ReportType:          reportType,
		ReportTitle:         fmt.Sprintf("Reporte de %s", report.Patient.NombresApellidos),
		ReportDescription:   "Reporte generado automáticamente",
		DateRange:           report.ReportPeriod,
		TotalSessions:       len(report.Sessions),
		CompletedSessions:   len(report.Sessions),
		AverageConfidence:   report.EmotionSummary.AverageConfidence,
		DominantEmotion:     report.EmotionSummary.DominantEmotion,
		EmotionTrend:        report.EmotionSummary.EmotionTrend,
		CurrentTherapistID:  report.Patient.ID, // Simplificación
		TherapistChanged:    false,
		FileFormat:          format,
		FileSizeBytes:       0,
	}

	if err := s.db.Create(&reportHistory).Error; err != nil {
		log.Printf("❌ Error guardando historial de reporte: %v", err)
	}
}


func (s *TherapyService) GenerateHistoricalReportPDF(reportData *models.PatientReportSummary) (string, error) {
	if reportData == nil {
		return "", fmt.Errorf("datos de reporte no disponibles")
	}

	
	htmlContent := fmt.Sprintf(`
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Reporte Histórico - %s</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
        .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #333; padding-bottom: 15px; }
        .patient-info { background: #f5f5f5; padding: 15px; margin-bottom: 20px; border-radius: 5px; }
        .section { margin-bottom: 25px; }
        .section h2 { color: #2c5aa0; border-bottom: 1px solid #ddd; padding-bottom: 5px; }
        .emotion-summary { background: #e8f4f8; padding: 15px; border-radius: 5px; margin: 15px 0; }
        .sessions-table { width: 100%%; border-collapse: collapse; margin: 10px 0; }
        .sessions-table th, .sessions-table td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        .sessions-table th { background-color: #f2f2f2; }
        .footer { margin-top: 30px; text-align: center; font-size: 12px; color: #666; }
    </style>
</head>
<body>
    <div class="header">
        <h1>REPORTE HISTÓRICO DEL PACIENTE</h1>
        <p>Sistema de Terapia - Análisis Emocional</p>
    </div>

    <div class="patient-info">
        <h2>Información del Paciente</h2>
        <p><strong>Nombre:</strong> %s</p>
        <p><strong>ID Serial:</strong> %s</p>
        <p><strong>Edad:</strong> %d años</p>
        <p><strong>Género:</strong> %s</p>
        <p><strong>Documento:</strong> %s - %s</p>
        <p><strong>Condición Médica:</strong> %s</p>
    </div>

    <div class="section">
        <h2>Resumen de Análisis Emocional</h2>
        <div class="emotion-summary">
            <p><strong>Total de Análisis:</strong> %d</p>
            <p><strong>Confianza Promedio:</strong> %.1f%%</p>
            <p><strong>Emoción Dominante:</strong> %s</p>
            <p><strong>Tendencia Emocional:</strong> %s</p>
            <p><strong>Confianza Más Alta:</strong> %.1f%%</p>
            <p><strong>Confianza Más Baja:</strong> %.1f%%</p>
        </div>
    </div>

    <div class="section">
        <h2>Historial de Sesiones (%d sesiones)</h2>
        <table class="sessions-table">
            <thead>
                <tr>
                    <th>Fecha</th>
                    <th>Tipo</th>
                    <th>Terapeuta</th>
                    <th>Estado</th>
                    <th>Emoción Detectada</th>
                    <th>Confianza</th>
                </tr>
            </thead>
            <tbody>
                %s
            </tbody>
        </table>
    </div>

    <div class="section">
        <h2>Información del Cuidador</h2>
        <p><strong>Cuidador Responsable:</strong> %s</p>
        <p><strong>Correo:</strong> %s</p>
        <p><strong>Teléfono:</strong> %s</p>
    </div>

    <div class="footer">
        <p>Reporte generado por: %s</p>
        <p>Fecha de generación: %s</p>
        <p>Período del reporte: %s</p>
        <hr>
        <p>Este reporte contiene información confidencial del paciente.</p>
    </div>
</body>
</html>`,
		reportData.Patient.NombresApellidos,
		reportData.Patient.NombresApellidos,
		reportData.Patient.SerialID,
		reportData.Patient.Edad,
		reportData.Patient.Genero,
		reportData.Patient.TipoDocumento,
		reportData.Patient.NumDocumento,
		reportData.Patient.CondicionMedica,
		reportData.EmotionSummary.TotalAnalyses,
		reportData.EmotionSummary.AverageConfidence,
		reportData.EmotionSummary.DominantEmotion,
		reportData.EmotionSummary.EmotionTrend,
		reportData.EmotionSummary.HighestConfidence,
		reportData.EmotionSummary.LowestConfidence,
		len(reportData.Sessions),
		generateSessionsTableRows(reportData.Sessions),
		reportData.Caregiver.NombresApellidos,
		reportData.Caregiver.Correo,
		reportData.Caregiver.Telefono,
		reportData.GeneratedBy.NombresApellidos,
		reportData.GeneratedAt.Format("02/01/2006 15:04:05"),
		reportData.ReportPeriod,
	)

	base64Content := base64.StdEncoding.EncodeToString([]byte(htmlContent))

	log.Printf("✅ PDF histórico generado exitosamente - %d caracteres en Base64", len(base64Content))
	return base64Content, nil
}


func generateSessionsTableRows(sessions []models.SessionWithAnalysis) string {
	var rows strings.Builder

	for _, session := range sessions {
		emotionText := "Sin análisis"
		confidenceText := "-"

		if session.EmotionAnalysis != nil {
			emotionText = session.EmotionAnalysis.EmotionType
			confidenceText = fmt.Sprintf("%.1f%%", session.EmotionAnalysis.ConfidencePercentage)
		}

		rows.WriteString(fmt.Sprintf(`
			<tr>
				<td>%s</td>
				<td>%s</td>
				<td>%s</td>
				<td>%s</td>
				<td>%s</td>
				<td>%s</td>
			</tr>`,
			session.Session.FechaSesion.Format("02/01/2006"),
			session.Session.TipoSesion,
			session.Session.Terapeuta.NombresApellidos,
			session.Session.Estado,
			emotionText,
			confidenceText,
		))
	}

	return rows.String()
}


func (s *TherapyService) getEmotionHistoryFromDatabase(patientID uint, dateFrom, dateTo *time.Time) *utils.PatientEmotionHistory {

	type EmotionDetectada struct {
		ID           uint      `gorm:"column:idemocion_detectada"`
		PacienteID   uint      `gorm:"column:paciente_id"`
		Emocion      uint      `gorm:"column:idemocion"`
		Porcentaje   float64   `gorm:"column:porcentaje"`
		ResponsableID *uint    `gorm:"column:responsable_user_id"`
		TherapySessionID *uint `gorm:"column:therapy_session_id"`
		CreatedAt    time.Time `gorm:"column:created_at"`
	}

	var emotions []EmotionDetectada
	query := s.db.Table("emociones_detectadas").Where("paciente_id = ?", patientID)

	if dateFrom != nil {
		query = query.Where("created_at >= ?", *dateFrom)
	}
	if dateTo != nil {
		query = query.Where("created_at <= ?", *dateTo)
	}

	if err := query.Find(&emotions).Error; err != nil {
		log.Printf("⚠️ Error consultando emociones_detectadas: %v", err)
		return nil
	}

	if len(emotions) == 0 {
		log.Printf("📊 No se encontraron emociones para el paciente %d", patientID)
		return nil
	}

	log.Printf("📊 Encontradas %d emociones para el paciente %d", len(emotions), patientID)


	emotionNames := map[uint]string{
		1:  "neutral",
		2:  "happy",
		3:  "sad",
		4:  "angry",
		5:  "fear",
		6:  "surprise",
		7:  "disgust",
		8:  "contempt",
		9:  "unknown",
		10: "calm",
		11: "excited", 
	}


	var analyses []utils.EmotionAnalysis
	emotionDistribution := make(map[string]int)
	var totalConfidence float64
	var mostFrequentEmotion string
	maxCount := 0

	for _, emotion := range emotions {
		emotionName, exists := emotionNames[emotion.Emocion]
		if !exists {
			emotionName = "unknown"
		}


		confidence := emotion.Porcentaje * 100

		var therapySessionIDInt *int
		if emotion.TherapySessionID != nil {
			temp := int(*emotion.TherapySessionID)
			therapySessionIDInt = &temp
		}

		var responsableUserIDInt *int
		if emotion.ResponsableID != nil {
			temp := int(*emotion.ResponsableID)
			responsableUserIDInt = &temp
		}

		analysis := utils.EmotionAnalysis{
			ID:                int(emotion.ID),
			EmotionName:       emotionName,
			Confidence:        confidence,
			RawConfidence:     emotion.Porcentaje,
			QualityStatus:     "high",
			SessionDate:       emotion.CreatedAt,
			PacienteID:        int(emotion.PacienteID),
			TherapySessionID:  therapySessionIDInt,
			ResponsableUserID: responsableUserIDInt,
			AlgorithmVersion:  "v2.0",
			SessionNotes:      fmt.Sprintf("Análisis emocional - %s con %.1f%% confianza", emotionName, confidence),
			SessionType:       "therapy",
			MeetsPrecisionTarget: confidence >= 95.0,
		}

		analyses = append(analyses, analysis)
		emotionDistribution[emotionName]++
		totalConfidence += confidence

		if emotionDistribution[emotionName] > maxCount {
			maxCount = emotionDistribution[emotionName]
			mostFrequentEmotion = emotionName
		}
	}

	avgConfidence := totalConfidence / float64(len(emotions))

	
	dateRange := "Últimos análisis"
	if dateFrom != nil && dateTo != nil {
		dateRange = fmt.Sprintf("%s - %s", dateFrom.Format("02/01/2006"), dateTo.Format("02/01/2006"))
	}

	return &utils.PatientEmotionHistory{
		TotalAnalyses:       len(emotions),
		AverageConfidence:   avgConfidence,
		MostFrequentEmotion: mostFrequentEmotion,
		EmotionDistribution: emotionDistribution,
		ConfidenceTrend:     "stable",
		Analyses:            analyses,
		DateRange:           dateRange,
		GeneratedAt:         time.Now(),
	}
}
