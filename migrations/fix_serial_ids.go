package migrations

import (
	"crypto/rand"
	"fmt"
	"log"
	"gorm.io/gorm"
	"github.com/tu-proyecto/models"
)

// FixExistingPatientSerialIDs genera serial_ids para pacientes existentes
func FixExistingPatientSerialIDs(db *gorm.DB) error {
	log.Println("Starting migration: Fix existing patient serial IDs...")

	// 1. Primero eliminar el índice si existe
	if db.Migrator().HasIndex(&models.Patient{}, "idx_patients_serial_id") {
		if err := db.Migrator().DropIndex(&models.Patient{}, "idx_patients_serial_id"); err != nil {
			log.Printf("Warning: Could not drop index: %v", err)
		}
	}

	// 2. Obtener todos los pacientes sin serial_id
	var patients []models.Patient
	if err := db.Where("serial_id IS NULL OR serial_id = ''").Find(&patients).Error; err != nil {
		return fmt.Errorf("failed to fetch patients: %v", err)
	}

	log.Printf("Found %d patients without serial_id", len(patients))

	// 3. Generar serial_id único para cada uno
	for i, patient := range patients {
		serialID := generateUniqueSerialID(db)
		if err := db.Model(&patient).Update("serial_id", serialID).Error; err != nil {
			return fmt.Errorf("failed to update patient %d: %v", patient.ID, err)
		}

		if (i+1) % 10 == 0 {
			log.Printf("Updated %d/%d patients", i+1, len(patients))
		}
	}

	// 4. Crear el índice único
	if err := db.Exec("CREATE UNIQUE INDEX idx_patients_serial_id ON patients(serial_id)").Error; err != nil {
		return fmt.Errorf("failed to create unique index: %v", err)
	}

	log.Printf("Successfully updated %d patients with serial_ids", len(patients))
	return nil
}

func generateUniqueSerialID(db *gorm.DB) string {
	for {
		// Generar 13 dígitos aleatorios
		b := make([]byte, 7)
		rand.Read(b)
		serialID := fmt.Sprintf("%013d", uint64(b[0])<<48|uint64(b[1])<<40|uint64(b[2])<<32|uint64(b[3])<<24|uint64(b[4])<<16|uint64(b[5])<<8|uint64(b[6]))[:13]

		// Verificar que no existe
		var count int64
		db.Model(&models.Patient{}).Where("serial_id = ?", serialID).Count(&count)
		if count == 0 {
			return serialID
		}
	}
}