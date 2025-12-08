package dto

import "time"

type CreatePatientDTO struct {
	NombresApellidos   string  `json:"nombres_apellidos" binding:"required,min=3,max=100"`
	FechaNacimiento    string  `json:"fecha_nacimiento" binding:"required"`
	TipoDocumento      string  `json:"tipo_documento" binding:"required"`
	NumDocumento       string  `json:"num_documento" binding:"required,min=1,max=20"`
	Altura             float32 `json:"altura" binding:"min=0,max=300"`
	Peso               float32 `json:"peso" binding:"min=0,max=500"`
	Sexo               string  `json:"sexo" binding:"required,oneof=Masculino Femenino M F Otro masculino femenino m f otro"`
	DiagnosticoClinico string  `json:"diagnostico_clinico"`
	FotoMovil          string  `json:"foto_movil"`
	FotoWeb            string  `json:"foto_web"`
	CuidadorID         *uint   `json:"cuidador_id"`
}

type UpdatePatientDTO struct {
	NombresApellidos   string  `json:"nombres_apellidos" binding:"omitempty,min=3,max=100"`
	FechaNacimiento    string  `json:"fecha_nacimiento" binding:"omitempty"`
	TipoDocumento      string  `json:"tipo_documento" binding:"omitempty"`
	NumDocumento       string  `json:"num_documento" binding:"omitempty,min=1,max=20"`
	Altura             float32 `json:"altura" binding:"omitempty,min=0,max=300"`
	Peso               float32 `json:"peso" binding:"omitempty,min=0,max=500"`
	Sexo               string  `json:"sexo" binding:"omitempty,oneof=Masculino Femenino M F Otro masculino femenino m f otro"`
	DiagnosticoClinico string  `json:"diagnostico_clinico"`
	FotoMovil          string  `json:"foto_movil"`
	FotoWeb            string  `json:"foto_web"`
	CuidadorID         *uint   `json:"cuidador_id"`
	Activo             *bool   `json:"activo"`
}

type PatientResponseDTO struct {
	ID                 uint       `json:"id"`
	SerialID           string     `json:"serial_id"`
	NombresApellidos   string     `json:"nombres_apellidos"`
	FechaNacimiento    string     `json:"fecha_nacimiento"`
	TipoDocumento      string     `json:"tipo_documento"`
	NumDocumento       string     `json:"num_documento"`
	Altura             float32    `json:"altura"`
	Peso               float32    `json:"peso"`
	IMC                float32    `json:"imc"`
	Sexo               string     `json:"sexo"`
	DiagnosticoClinico string     `json:"diagnostico_clinico"`
	FotoMovil          string     `json:"foto_movil"`
	FotoWeb            string     `json:"foto_web"`
	TerapeutaID        uint       `json:"terapeuta_id"`
	TerapeutaNombre    string     `json:"terapeuta_nombre"`
	CuidadorID         *uint      `json:"cuidador_id"`
	CuidadorNombre     *string    `json:"cuidador_nombre,omitempty"`
	Activo             bool       `json:"activo"`
	CreatedAt          time.Time  `json:"created_at"`
	UpdatedAt          time.Time  `json:"updated_at"`
}

type PatientListDTO struct {
	ID               uint    `json:"id"`
	SerialID         string  `json:"serial_id"`
	NombresApellidos string  `json:"nombres_apellidos"`
	TipoDocumento    string  `json:"tipo_documento"`
	NumDocumento     string  `json:"num_documento"`
	Edad             int     `json:"edad"`
	Sexo             string  `json:"sexo"`
	TerapeutaID      uint    `json:"terapeuta_id"`
	TerapeutaNombre  string  `json:"terapeuta_nombre"`
	CuidadorNombre   *string `json:"cuidador_nombre,omitempty"`
	Activo           bool    `json:"activo"`
	FotoMovil        string  `json:"foto_movil"`
	FotoWeb          string  `json:"foto_web"`
}