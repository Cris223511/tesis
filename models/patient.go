package models

import (
	"time"
	"gorm.io/gorm"
)

type Patient struct {
	ID                uint                 `gorm:"primaryKey;autoIncrement" json:"id"`
	NombresApellidos  string               `gorm:"size:100;not null;index" json:"nombres_apellidos"`
	FechaNacimiento   FechaNacimiento      `json:"fecha_nacimiento"`
	TipoDocumento     string               `gorm:"size:20;not null" json:"tipo_documento"`
	NumDocumento      string               `gorm:"size:50;uniqueIndex;not null" json:"num_documento"`
	Altura            float32              `gorm:"type:decimal(5,2)" json:"altura"`
	Peso              float32              `gorm:"type:decimal(5,2)" json:"peso"`
	IMC               float32              `gorm:"type:decimal(5,2)" json:"imc"`
	Sexo              string               `gorm:"size:20;not null" json:"sexo"`
	DiagnosticoClinico string              `gorm:"type:text" json:"diagnostico_clinico"`
	Foto              string               `gorm:"type:mediumtext" json:"foto"`
	TerapeutaID       uint                 `gorm:"not null;index" json:"terapeuta_id"`
	CuidadorID        *uint                `gorm:"index" json:"cuidador_id"`
	Activo            bool                 `gorm:"default:true" json:"activo"`
	CreatedAt         time.Time            `gorm:"index" json:"created_at"`
	UpdatedAt         time.Time            `json:"updated_at"`
	DeletedAt         gorm.DeletedAt       `gorm:"index" json:"-"`

	Terapeuta         Usuarios             `gorm:"foreignKey:TerapeutaID;constraint:OnUpdate:CASCADE,OnDelete:RESTRICT" json:"terapeuta,omitempty"`
	Cuidador          *Usuarios            `gorm:"foreignKey:CuidadorID;constraint:OnUpdate:CASCADE,OnDelete:SET NULL" json:"cuidador,omitempty"`
}

func (p *Patient) TableName() string {
	return "patients"
}

func (p *Patient) CalculateIMC() {
	if p.Altura > 0 && p.Peso > 0 {
		p.IMC = p.Peso / (p.Altura * p.Altura)
	}
}

func (p *Patient) BeforeSave(tx *gorm.DB) error {
	p.CalculateIMC()
	return nil
}