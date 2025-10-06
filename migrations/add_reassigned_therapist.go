package migrations

import (
	"log"
	"gorm.io/gorm"
)

// AddReassignedTherapistColumn agrega la columna terapeuta_reasignado_id a therapy_sessions
func AddReassignedTherapistColumn(db *gorm.DB) error {
	log.Println("Starting migration: Add terapeuta_reasignado_id column...")

	// Verificar si la columna ya existe
	if db.Migrator().HasColumn(&struct{}{}, "terapeuta_reasignado_id") {
		log.Println("Column terapeuta_reasignado_id already exists, skipping...")
		return nil
	}

	// Agregar la columna
	sql := `
		ALTER TABLE therapy_sessions
		ADD COLUMN terapeuta_reasignado_id INT UNSIGNED NULL,
		ADD INDEX idx_terapeuta_reasignado (terapeuta_reasignado_id),
		ADD CONSTRAINT fk_therapy_sessions_terapeuta_reasignado
			FOREIGN KEY (terapeuta_reasignado_id)
			REFERENCES usuarios(idusuario)
			ON UPDATE CASCADE
			ON DELETE SET NULL;
	`

	if err := db.Exec(sql).Error; err != nil {
		return err
	}

	log.Println("Successfully added terapeuta_reasignado_id column")
	return nil
}
