package config

import (
	"fmt"
	"log"
	"os"
	"time"
	"usuarios/models"

	"github.com/joho/godotenv"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
)


var DB *gorm.DB

func LoadEnv() {
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found")
	}
}

func InitializeDatabase() {
	LoadEnv()
	dsn := fmt.Sprintf(
		"%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		os.Getenv("DB_USER"),
		os.Getenv("DB_PASSWORD"),
		os.Getenv("DB_HOST"),
		os.Getenv("DB_PORT"),
		os.Getenv("DB_NAME"),
	)
	
	db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{
		DisableForeignKeyConstraintWhenMigrating: true,
	})
	if err != nil {
		log.Fatalf("Error al conectar a la base de datos: %v", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		log.Fatalf("Error obteniendo instancia de base de datos: %v", err)
	}

	sqlDB.SetMaxOpenConns(3)
	sqlDB.SetMaxIdleConns(2)
	sqlDB.SetConnMaxLifetime(5 * time.Minute)
	sqlDB.SetConnMaxIdleTime(2 * time.Minute)

	log.Println("Pool de conexiones configurado: MaxOpen=3, MaxIdle=2")

	DB = db
	
	if err := DB.Exec("SET FOREIGN_KEY_CHECKS = 0").Error; err != nil {
		log.Fatalf("Error deshabilitando foreign keys: %v", err)
	}
	
	if err := DB.SetupJoinTable(&models.Usuarios{}, "Roles", &models.UserRole{}); err != nil {
		log.Fatalf("Error configurando join table: %v", err)
	}
	
	if err := DB.AutoMigrate(&models.Role{}); err != nil {
		log.Fatalf("Error al migrar roles: %v", err)
	}
	
	if err := DB.AutoMigrate(&models.Usuarios{}); err != nil {
		log.Fatalf("Error al migrar usuarios: %v", err)
	}
	
	
	if err := DB.AutoMigrate(
		&models.UserRole{},
		&models.UserUnblockCooldown{},
		&models.UserDeviceIP{},
		&models.LoginHistory{},
		&models.SecurityLog{},
		&models.PasswordHistory{},
		&models.OTP{},
		&models.OTPResend{},
		&models.UserRelationship{},
		&models.PhotoChange{},
		&models.BannerChange{},
		&models.ProfileChange{},
		&models.ReportHistory{},
	); err != nil {
		log.Fatalf("Error al migrar tablas relacionadas: %v", err)
	}
	
	if err := DB.Exec("SET FOREIGN_KEY_CHECKS = 1").Error; err != nil {
		log.Fatalf("Error habilitando foreign keys: %v", err)
	}
	
	log.Println("Base de datos conectada y migrada exitosamente")
}