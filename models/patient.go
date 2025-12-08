package models

import (
	"crypto/rand"
	"fmt"
	"time"
	"gorm.io/gorm"
)

type Patient struct {
	ID                uint                 `gorm:"primaryKey;autoIncrement" json:"id"`
	SerialID          string               `gorm:"size:13;index;not null;default:''" json:"serial_id"`
	NombresApellidos  string               `gorm:"size:100;not null;index" json:"nombres_apellidos"`
	FechaNacimiento   FechaNacimiento      `json:"fecha_nacimiento"`
	TipoDocumento     string               `gorm:"size:20;not null" json:"tipo_documento"`
	NumDocumento      string               `gorm:"size:50;uniqueIndex;not null" json:"num_documento"`
	Altura            float32              `gorm:"type:decimal(5,2)" json:"altura"`
	Peso              float32              `gorm:"type:decimal(5,2)" json:"peso"`
	IMC               float32              `gorm:"type:decimal(5,2)" json:"imc"`
	Sexo              string               `gorm:"size:20;not null" json:"sexo"`
	DiagnosticoClinico string              `gorm:"type:text" json:"diagnostico_clinico"`
	FotoMovil         string               `gorm:"type:mediumtext;column:foto_movil" json:"foto_movil"`
	FotoWeb           string               `gorm:"type:mediumtext;column:foto_web" json:"foto_web"`
	TerapeutaID       uint                 `gorm:"not null;index" json:"terapeuta_id"`
	CuidadorID        *uint                `gorm:"column:responsable_id;index" json:"cuidador_id"`
	Activo            bool                 `gorm:"default:true" json:"activo"`
	CreatedAt         time.Time            `gorm:"index" json:"created_at"`
	UpdatedAt         time.Time            `json:"updated_at"`
	DeletedAt         gorm.DeletedAt       `gorm:"index" json:"-"`

	Terapeuta         Usuarios             `gorm:"foreignKey:TerapeutaID;references:ID" json:"terapeuta,omitempty"`
	Cuidador          *Usuarios            `gorm:"foreignKey:CuidadorID;references:ID" json:"cuidador,omitempty"`
}

func (p *Patient) TableName() string {
	return "patients"
}

func (p *Patient) CalculateIMC() {
	if p.Altura > 0 && p.Peso > 0 {
		p.IMC = p.Peso / (p.Altura * p.Altura)
	}
}

func (p *Patient) BeforeCreate(tx *gorm.DB) error {
	// Generar serial_id único de 13 dígitos si no existe
	if p.SerialID == "" {
		p.SerialID = GenerateSerialID(tx)
	}
	p.CalculateIMC()
	return nil
}

func (p *Patient) BeforeSave(tx *gorm.DB) error {
	p.CalculateIMC()
	return nil
}

// GenerateSerialID genera un ID único de 13 dígitos
func GenerateSerialID(db *gorm.DB) string {
	for {
		// Generar 13 dígitos aleatorios
		b := make([]byte, 7) // 7 bytes dan suficiente entropía
		rand.Read(b)
		serialID := fmt.Sprintf("%013d", uint64(b[0])<<48|uint64(b[1])<<40|uint64(b[2])<<32|uint64(b[3])<<24|uint64(b[4])<<16|uint64(b[5])<<8|uint64(b[6]))[:13]

		// Verificar que no existe en la base de datos
		var count int64
		db.Model(&Patient{}).Where("serial_id = ?", serialID).Count(&count)
		if count == 0 {
			return serialID
		}
		// Si ya existe, generar otro
	}
}

// FixMissingSerialIDs actualiza pacientes sin serial_id
func FixMissingSerialIDs(db *gorm.DB) error {
	var patients []Patient

	// Buscar pacientes sin serial_id
	if err := db.Where("serial_id IS NULL OR serial_id = ''").Find(&patients).Error; err != nil {
		return fmt.Errorf("error buscando pacientes sin serial_id: %v", err)
	}

	// Actualizar cada uno con un serial_id único
	for _, patient := range patients {
		serialID := GenerateSerialID(db)
		if err := db.Model(&patient).Update("serial_id", serialID).Error; err != nil {
			return fmt.Errorf("error actualizando serial_id para paciente %d: %v", patient.ID, err)
		}
	}

	if len(patients) > 0 {
		fmt.Printf("Actualizados %d pacientes con serial_id\n", len(patients))
	}

	return nil
}