package services

import (
	"errors"
	"log"
	"math"
	"strings"
	"time"

	"usuarios/dto"
	"usuarios/models"
	"gorm.io/gorm"
)

type PatientService struct {
	db *gorm.DB
}

type PaginatedPatientsResult struct {
	Patients    []models.Patient `json:"patients"`
	Total       int64            `json:"total"`
	TotalPages  int              `json:"total_pages"`
	HasNext     bool             `json:"has_next"`
	HasPrevious bool             `json:"has_previous"`
}

func NewPatientService(db *gorm.DB) *PatientService {
	return &PatientService{db: db}
}

func (s *PatientService) calculateIMC(peso, altura float32) float32 {
	if altura <= 0 || peso <= 0 {
		return 0
	}
	alturaMetros := altura / 100
	imc := peso / (alturaMetros * alturaMetros)
	return float32(math.Round(float64(imc)*100) / 100)
}

func (s *PatientService) Create(dto *dto.CreatePatientDTO, terapeutaID uint) (*models.Patient, error) {
	if dto.CuidadorID != nil {
		var count int64
		if err := s.db.Model(&models.Patient{}).Where("cuidador_id = ?", *dto.CuidadorID).Count(&count).Error; err != nil {
			return nil, err
		}
		if count >= 5 {
			return nil, errors.New("el cuidador ha alcanzado el límite máximo de 5 pacientes")
		}
	}
	fechaNac, err := time.Parse("2006-01-02", dto.FechaNacimiento)
	if err != nil {
		return nil, errors.New("formato de fecha inválido")
	}

	// Validar edad (18-60 años para pacientes)
	now := time.Now()
	age := now.Year() - fechaNac.Year()
	if now.YearDay() < fechaNac.YearDay() {
		age--
	}

	if age < 0 {
		return nil, errors.New("la fecha de nacimiento no puede ser futura")
	}
	if age < 18 {
		return nil, errors.New("el paciente debe tener al menos 18 años")
	}
	if age > 60 {
		return nil, errors.New("la edad no puede ser mayor a 60 años")
	}

	imc := s.calculateIMC(dto.Peso, dto.Altura)

	patient := &models.Patient{
		NombresApellidos:   dto.NombresApellidos,
		FechaNacimiento:    models.FechaNacimiento(fechaNac),
		TipoDocumento:      dto.TipoDocumento,
		NumDocumento:       dto.NumDocumento,
		Altura:             dto.Altura,
		Peso:               dto.Peso,
		IMC:                imc,
		Sexo:               dto.Sexo,
		DiagnosticoClinico: dto.DiagnosticoClinico,
		FotoMovil:          dto.FotoMovil,
		FotoWeb:            dto.FotoWeb,
		TerapeutaID:        terapeutaID,
		CuidadorID:         dto.CuidadorID,
		Activo:             true,
	}

	if err := s.db.Create(patient).Error; err != nil {
		if errors.Is(err, gorm.ErrDuplicatedKey) {
			return nil, errors.New("documento ya registrado")
		}
		return nil, err
	}

	return s.GetByID(patient.ID)
}

func (s *PatientService) GetAll(userID uint, roles []string) ([]models.Patient, error) {
	var patients []models.Patient
	query := s.db.
		Preload("Terapeuta").
		Preload("Cuidador")


	if s.hasRole(roles, "AD") {
		
	} else if s.hasRole(roles, "TR") {
		
		query = query.Where("terapeuta_id = ?", userID)
	} else if s.hasRole(roles, "PD") {
		
		query = query.Where("cuidador_id = ?", userID)
	}

	if err := query.Find(&patients).Error; err != nil {
		return nil, err
	}

	return patients, nil
}

func (s *PatientService) GetAllPaginated(userID uint, roles []string, page, limit int, search string) (*PaginatedPatientsResult, error) {
	var patients []models.Patient
	var total int64

	baseQuery := s.db.Model(&models.Patient{})

	// Filtrar por rol
	if s.hasRole(roles, "AD") {
		// Administrador ve todos los pacientes
	} else if s.hasRole(roles, "TR") {
		// Terapeuta solo ve sus pacientes asignados
		baseQuery = baseQuery.Where("terapeuta_id = ?", userID)
	} else if s.hasRole(roles, "PD") {
		// Cuidador/Padre solo ve sus pacientes asignados
		baseQuery = baseQuery.Where("cuidador_id = ?", userID)
	}

	if search != "" {
		searchPattern := "%" + search + "%"
		baseQuery = baseQuery.Where(
			"nombres_apellidos LIKE ? OR num_documento LIKE ? OR diagnostico_clinico LIKE ?",
			searchPattern, searchPattern, searchPattern,
		)
	}

	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, err
	}

	offset := (page - 1) * limit
	if err := baseQuery.
		Preload("Terapeuta").
		Preload("Cuidador").
		Offset(offset).
		Limit(limit).
		Find(&patients).Error; err != nil {
		return nil, err
	}

	totalPages := int(math.Ceil(float64(total) / float64(limit)))
	hasNext := page < totalPages
	hasPrevious := page > 1

	return &PaginatedPatientsResult{
		Patients:    patients,
		Total:       total,
		TotalPages:  totalPages,
		HasNext:     hasNext,
		HasPrevious: hasPrevious,
	}, nil
}

func (s *PatientService) GetByID(id uint) (*models.Patient, error) {
	var patient models.Patient
	err := s.db.Preload("Terapeuta").Preload("Cuidador").First(&patient, id).Error
	if err != nil {
		log.Printf("[PATIENT][GetByID] Error: %v", err)
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errors.New("paciente no encontrado")
		}
		return nil, err
	}
	log.Printf("[PATIENT][GetByID] Patient found: ID=%d, TerapeutaID=%d, TerapeutaNombre=%s", patient.ID, patient.TerapeutaID, patient.Terapeuta.Nombres_Apellidos)
	return &patient, nil
}

func (s *PatientService) Update(id uint, dto *dto.UpdatePatientDTO) (*models.Patient, error) {
	// Buscar paciente directamente sin preloads
	var patient models.Patient
	if err := s.db.First(&patient, id).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errors.New("paciente no encontrado")
		}
		return nil, err
	}

	// Aplicar cambios directamente al struct
	if dto.NombresApellidos != "" {
		patient.NombresApellidos = dto.NombresApellidos
	}

	if dto.FechaNacimiento != "" {
		fechaNac, err := time.Parse("2006-01-02", dto.FechaNacimiento)
		if err != nil {
			return nil, errors.New("formato de fecha inválido")
		}
		// Validar edad
		now := time.Now()
		age := now.Year() - fechaNac.Year()
		if now.YearDay() < fechaNac.YearDay() {
			age--
		}
		if age < 0 {
			return nil, errors.New("la fecha de nacimiento no puede ser futura")
		}
		if age < 18 {
			return nil, errors.New("el paciente debe tener al menos 18 años")
		}
		if age > 60 {
			return nil, errors.New("la edad no puede ser mayor a 60 años")
		}
		patient.FechaNacimiento = models.FechaNacimiento(fechaNac)
	}

	if dto.TipoDocumento != "" {
		patient.TipoDocumento = dto.TipoDocumento
	}
	if dto.NumDocumento != "" {
		patient.NumDocumento = dto.NumDocumento
	}
	if dto.Altura > 0 {
		patient.Altura = dto.Altura
	}
	if dto.Peso > 0 {
		patient.Peso = dto.Peso
	}
	if dto.Sexo != "" {
		patient.Sexo = dto.Sexo
	}
	if dto.DiagnosticoClinico != "" {
		patient.DiagnosticoClinico = dto.DiagnosticoClinico
	}
	if dto.FotoMovil != "" {
		patient.FotoMovil = dto.FotoMovil
	}
	if dto.FotoWeb != "" {
		patient.FotoWeb = dto.FotoWeb
	}
	if dto.CuidadorID != nil {
		if *dto.CuidadorID != 0 && (patient.CuidadorID == nil || *patient.CuidadorID != *dto.CuidadorID) {
			var count int64
			s.db.Model(&models.Patient{}).Where("cuidador_id = ?", *dto.CuidadorID).Count(&count)
			if count >= 5 {
				return nil, errors.New("el cuidador ha alcanzado el límite máximo de 5 pacientes")
			}
		}
		patient.CuidadorID = dto.CuidadorID
	}
	if dto.Activo != nil {
		patient.Activo = *dto.Activo
	}

	// Recalcular IMC si cambió peso o altura
	if dto.Altura > 0 || dto.Peso > 0 {
		patient.IMC = s.calculateIMC(patient.Peso, patient.Altura)
	}

	// Guardar todos los cambios de una vez
	if err := s.db.Save(&patient).Error; err != nil {
		return nil, err
	}

	// Cargar relaciones para la respuesta
	s.db.Preload("Terapeuta").Preload("Cuidador").First(&patient, id)

	return &patient, nil
}

func (s *PatientService) Delete(id uint) error {
	result := s.db.Delete(&models.Patient{}, id)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return errors.New("paciente no encontrado")
	}
	return nil
}

func (s *PatientService) CanAccess(patientID, userID uint, roles []string) bool {
	if s.hasRole(roles, "TR") || s.hasRole(roles, "AD") {
		return true
	}

	if s.hasRole(roles, "PD") {
		var patient models.Patient
		err := s.db.First(&patient, patientID).Error
		if err != nil {
			return false
		}
		return patient.CuidadorID != nil && *patient.CuidadorID == userID
	}

	return false
}

func (s *PatientService) hasRole(roles []string, targetRole string) bool {
	for _, role := range roles {
		role = strings.TrimSpace(role)
		roleLower := strings.ToLower(role)
		targetLower := strings.ToLower(targetRole)

		// Verificar coincidencia exacta
		if role == targetRole || roleLower == targetLower {
			return true
		}

		// Verificar códigos y nombres completos
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