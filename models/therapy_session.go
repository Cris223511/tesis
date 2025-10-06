package models

import (
	"time"
	"encoding/json"
	"database/sql/driver"
	"errors"
)


type StringArray []string

func (a StringArray) Value() (driver.Value, error) {
	if a == nil {
		return nil, nil
	}
	return json.Marshal(a)
}

func (a *StringArray) Scan(value interface{}) error {
	if value == nil {
		*a = nil
		return nil
	}

	switch v := value.(type) {
	case []byte:
		return json.Unmarshal(v, a)
	case string:
		return json.Unmarshal([]byte(v), a)
	default:
		return errors.New("cannot scan into StringArray")
	}
}


type TherapySession struct {
	ID                     uint         `gorm:"primaryKey;autoIncrement" json:"id"`
	PacienteID             uint         `gorm:"not null;index" json:"paciente_id"`
	TerapeutaID            uint         `gorm:"not null;index" json:"terapeuta_id"`
	TerapeutaReasignadoID  *uint        `gorm:"index" json:"terapeuta_reasignado_id,omitempty"`

	FechaSesion     time.Time    `gorm:"not null;index" json:"fecha_sesion"`
	HoraInicio      string       `gorm:"size:10;not null" json:"hora_inicio"`
	HoraFin         string       `gorm:"size:10;not null" json:"hora_fin"`
	Duracion        int          `gorm:"not null" json:"duracion"`

	Ubicacion       string       `gorm:"size:200;not null" json:"ubicacion"`
	Direccion       string       `gorm:"type:text;not null" json:"direccion"`

	Descripcion     string       `gorm:"type:text;not null" json:"descripcion"`
	Objetivos       StringArray  `gorm:"type:json;not null" json:"objetivos"`
	Materiales      StringArray  `gorm:"type:json;not null" json:"materiales"`
	NotasTerapeuta  string       `gorm:"type:text" json:"notas_terapeuta"`

	Estado          string       `gorm:"size:20;default:'programada';index" json:"estado"`
	TipoSesion      string       `gorm:"size:50;not null" json:"tipo_sesion"`
	Modalidad       string       `gorm:"size:30;not null" json:"modalidad"`
	UpdateCount     int          `gorm:"default:0" json:"update_count"`
	IsDeleted       bool         `gorm:"default:false;index" json:"is_deleted"`

	CreatedAt       time.Time    `gorm:"index" json:"created_at"`
	UpdatedAt       time.Time    `json:"updated_at"`

	Paciente            Patient      `gorm:"foreignKey:PacienteID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"paciente,omitempty"`
	Terapeuta           Usuarios     `gorm:"foreignKey:TerapeutaID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"terapeuta,omitempty"`
	TerapeutaReasignado *Usuarios    `gorm:"foreignKey:TerapeutaReasignadoID;constraint:OnUpdate:CASCADE,OnDelete:SET NULL" json:"terapeuta_reasignado,omitempty"`
}

func (t *TherapySession) TableName() string {
	return "therapy_sessions"
}