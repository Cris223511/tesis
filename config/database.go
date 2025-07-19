package config

import (
	"fmt"
	"log"
	"os"

	"github.com/joho/godotenv"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"usuarios/models"

)

var DB *gorm.DB


func LoadEnv() {
	// Esto NO rompe en producción, solo loguea.
	err := godotenv.Load()
	if err != nil {
		log.Println("No se encontró .env, usando variables de entorno del sistema")
	}
}


// InitializeDatabase conecta a la base de datos usando las variables de entorno
func InitializeDatabase() {
	// Cargar variables de entorno
	LoadEnv()

	// Obtener los valores desde el .env
	dbUser := os.Getenv("DB_USER")
	dbPassword := os.Getenv("DB_PASSWORD")
	dbHost := os.Getenv("DB_HOST")
	dbPort := os.Getenv("DB_PORT")
	dbName := os.Getenv("DB_NAME")

	// Construir DSN con los valores del .env
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		dbUser, dbPassword, dbHost, dbPort, dbName)

	// Conectar con la base de datos
	database, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
	if err != nil {
		log.Fatalf("Error al conectar a la base de datos: %v", err)
	}

	DB = database

	// Migrar modelos
	if err := DB.AutoMigrate(
		
		&models.User{},
		&models.Role{},
		&models.UserUnblockCooldown{},
		&models.BiometricCredential{},
		&models.UserDeviceIP{},
		&models.LoginHistory{},
		&models.SecurityLog{},
		&models.PasswordHistory{},
		

		

		
	
		); err != nil {
		log.Fatalf("Error al migrar la base de datos: %v", err)
	}

	log.Println("Base de datos conectada exitosamente!")
}