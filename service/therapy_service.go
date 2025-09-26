package services

import (
	"errors"
	"fmt"
	"log"
	"math"
	"strings"
	"time"
	"usuarios/dto"
	"usuarios/models"
	"gorm.io/gorm"
)

type TherapyService struct {
	db           *gorm.DB
	emailService *EmailService
}

func NewTherapyService(db *gorm.DB) *TherapyService {
	return &TherapyService{
		db:           db,
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
	query := s.db.Preload("Paciente").Preload("Paciente.Cuidador").Preload("Terapeuta").Where("is_deleted = ?", false)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Joins("JOIN patients ON patients.id = therapy_sessions.paciente_id").
			Where("patients.cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		query = query.Where("terapeuta_id = ?", userID)
	}

	if err := query.Order("fecha_sesion DESC, hora_inicio ASC").Find(&sessions).Error; err != nil {
		return nil, err
	}

	log.Printf("[DEBUG] GetAll loaded %d sessions", len(sessions))
	for i, session := range sessions {
		if session.Paciente.CuidadorID != nil {
			caregiverLoaded := session.Paciente.Cuidador != nil
			log.Printf("[DEBUG] Session %d: Paciente %s, CuidadorID: %d, Cuidador loaded: %v",
				session.ID, session.Paciente.NombresApellidos, *session.Paciente.CuidadorID, caregiverLoaded)
		} else {
			log.Printf("[DEBUG] Session %d: Paciente %s, sin cuidador asignado", session.ID, session.Paciente.NombresApellidos)
		}
		if i >= 3 { // Solo mostrar los primeros 3 para no saturar los logs
			break
		}
	}

	return sessions, nil
}

func (s *TherapyService) GetPaginated(userID uint, roles []string, search *dto.SearchSessionsDTO) (*dto.PaginatedSessionsResponse, error) {
	var sessions []models.TherapySession
	var total int64

	query := s.db.Preload("Paciente").Preload("Paciente.Cuidador").Preload("Terapeuta").Where("is_deleted = ?", false)

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
		sessionResponses[i] = s.toSessionResponse(session)
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
	query := s.db.Preload("Paciente").Preload("Paciente.Cuidador").Preload("Terapeuta").Where("is_deleted = ?", false)

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

	log.Printf("[DEBUG] GetByID - Session %d loaded successfully", session.ID)
	if session.Paciente.CuidadorID != nil {
		caregiverLoaded := session.Paciente.Cuidador != nil
		log.Printf("[DEBUG] GetByID - Paciente: %s, CuidadorID: %d, Cuidador loaded: %v",
			session.Paciente.NombresApellidos, *session.Paciente.CuidadorID, caregiverLoaded)
		if caregiverLoaded {
			log.Printf("[DEBUG] GetByID - Cuidador details: %s (ID: %d)",
				session.Paciente.Cuidador.Nombres_Apellidos, session.Paciente.Cuidador.ID)
		}
	} else {
		log.Printf("[DEBUG] GetByID - Paciente %s sin cuidador asignado", session.Paciente.NombresApellidos)
	}

	return &session, nil
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
	query := s.db.Preload("Terapeuta").Preload("Cuidador").Limit(3).Order("created_at DESC")

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

func (s *TherapyService) toSessionResponse(session models.TherapySession) dto.SessionResponse {
	// Log para debugging de datos del cuidador
	log.Printf("[DEBUG] Session ID: %d, Paciente: %s, PacienteID: %d",
		session.ID, session.Paciente.NombresApellidos, session.Paciente.ID)

	if session.Paciente.CuidadorID != nil {
		log.Printf("[DEBUG] Paciente tiene CuidadorID: %d", *session.Paciente.CuidadorID)
		if session.Paciente.Cuidador != nil {
			log.Printf("[DEBUG] Cuidador cargado: %s (ID: %d)",
				session.Paciente.Cuidador.Nombres_Apellidos, session.Paciente.Cuidador.ID)
		} else {
			log.Printf("[WARNING] CuidadorID existe pero Cuidador es nil - problema de preload")
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
			Foto:             session.Paciente.Foto,
		},
		Terapeuta: dto.UserBasicInfo{
			ID:                session.Terapeuta.ID,
			NombresApellidos:  session.Terapeuta.Nombres_Apellidos,
			Correo:            session.Terapeuta.Correo,
			Telefono:          session.Terapeuta.Telefono,
		},
	}

	// Agregar información del cuidador si existe
	if session.Paciente.Cuidador != nil {
		response.Cuidador = &dto.UserBasicInfo{
			ID:                session.Paciente.Cuidador.ID,
			NombresApellidos:  session.Paciente.Cuidador.Nombres_Apellidos,
			Correo:            session.Paciente.Cuidador.Correo,
			Telefono:          session.Paciente.Cuidador.Telefono,
		}
		log.Printf("[DEBUG] Cuidador agregado a respuesta: %s", response.Cuidador.NombresApellidos)
	} else {
		log.Printf("[DEBUG] No se agregó cuidador a la respuesta")
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
	// Buscar SOLO usuarios con rol de terapeuta
	var therapists []models.Usuarios
	query := s.db.Table("usuarios").
		Select("DISTINCT usuarios.idusuario, usuarios.nombres_apellidos, usuarios.correo, usuarios.telefono").
		Joins("JOIN user_roles ON usuarios.idusuario = user_roles.usuarios_id_usuario").
		Joins("JOIN roles ON user_roles.roles_id = roles.id").
		Where("UPPER(roles.name) = 'TR'"). // Solo terapeutas exactamente
		Where("usuarios.activo = 0").
		Having("(SELECT COUNT(*) FROM patients WHERE terapeuta_id = usuarios.idusuario) < 20") // Verificar límite de 20 pacientes

	if err := query.Find(&therapists).Error; err != nil {
		log.Printf("[ERROR] Error al obtener terapeutas: %v", err)
		return nil, err
	}

	log.Printf("[DEBUG] Terapeutas disponibles encontrados: %d", len(therapists))
	for _, t := range therapists {
		// Verificar cuántos pacientes tiene este terapeuta
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
		}
	}

	return result, nil
}

// toPatientListDTO transforms a Patient model to PatientListDTO with calculated age
func (s *TherapyService) toPatientListDTO(patient models.Patient) dto.PatientListDTO {
	// Calculate age from birth date
	now := time.Now()
	birthDate := time.Time(patient.FechaNacimiento)
	age := now.Year() - birthDate.Year()

	// Adjust age if birthday hasn't occurred this year yet
	if now.YearDay() < birthDate.YearDay() {
		age--
	}

	// Ensure age is not negative
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
		Foto:             patient.Foto,
	}
}

// GetAllActiveUsers obtiene todos los usuarios activos (sin filtro de rol)
func (s *TherapyService) GetAllActiveUsers() ([]dto.UserBasicInfo, error) {
	var users []models.Usuarios

	// Simplemente obtener todos los usuarios activos
	query := s.db.Table("usuarios").
		Select("usuarios.idusuario, usuarios.nombres_apellidos, usuarios.correo, usuarios.telefono").
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
		}
	}

	return result, nil
}

// DebugCaregiverPreload - función temporal para debug del preload de cuidadores
func (s *TherapyService) DebugCaregiverPreload() error {
	var sessions []models.TherapySession

	// Test query con preload explícito
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
		log.Printf("[DEBUG] CuidadorID: %v", session.Paciente.CuidadorID)
		if session.Paciente.CuidadorID != nil {
			log.Printf("[DEBUG] CuidadorID value: %d", *session.Paciente.CuidadorID)
			if session.Paciente.Cuidador != nil {
				log.Printf("[DEBUG] ✅ Cuidador loaded: %s (ID: %d)",
					session.Paciente.Cuidador.Nombres_Apellidos, session.Paciente.Cuidador.ID)
			} else {
				log.Printf("[DEBUG] ❌ Cuidador NOT loaded despite CuidadorID being set")

				// Direct query to check if caregiver exists
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
		Where("UPPER(roles.name) IN ('TR', 'AD')"). // Solo terapeutas o administradores
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
	// Verificar que el usuario tenga acceso al paciente
	var patient models.Patient
	patientQuery := s.db.Where("id = ? AND activo = ?", patientID, true)

	if s.hasRole(roles, "PD") && !s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		// Padres: solo sus pacientes asignados
		patientQuery = patientQuery.Where("cuidador_id = ?", userID)
	} else if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		// Terapeutas: solo pacientes de sus sesiones
		patientQuery = patientQuery.Joins("JOIN therapy_sessions ON therapy_sessions.paciente_id = patients.id").
			Where("therapy_sessions.terapeuta_id = ? AND therapy_sessions.is_deleted = ?", userID, false)
	}
	// Admin ve todos los pacientes

	err := patientQuery.First(&patient).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errors.New("paciente no encontrado o sin acceso")
		}
		return nil, err
	}

	// Obtener estadísticas de sesiones
	var sessions []models.TherapySession
	sessionQuery := s.db.Where("paciente_id = ? AND is_deleted = ?", patientID, false)

	// Aplicar filtro de acceso para sesiones
	if s.hasRole(roles, "TR") && !s.hasRole(roles, "AD") {
		sessionQuery = sessionQuery.Where("terapeuta_id = ?", userID)
	}

	err = sessionQuery.Find(&sessions).Error
	if err != nil {
		return nil, err
	}

	// Calcular estadísticas
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

	// Calcular porcentaje de progreso (sesiones completadas vs total)
	progressPercentage := 0
	if totalSessions > 0 {
		progressPercentage = (completedSessions * 100) / totalSessions
	}

	// Calcular días desde la última sesión
	daysSinceLastSession := 0
	lastSessionDateStr := ""
	if lastSessionDate != nil {
		daysSinceLastSession = int(time.Since(*lastSessionDate).Hours() / 24)
		lastSessionDateStr = lastSessionDate.Format("2006-01-02")
	}

	// Calcular duración promedio
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