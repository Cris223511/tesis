package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"usuarios/config"
	"usuarios/controllers"
	"usuarios/docs"
	"usuarios/models"
	"usuarios/routes"
	"usuarios/service"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)






type RateLimiter struct {
	mu             sync.Mutex
	dailyCounts    map[uint][]time.Time
	moduleCounts   map[string][]time.Time
	moduleBlocked  map[string]time.Time
	dailyLimit     int
	repeatLimit    int
	repeatWindow   time.Duration
	blockDuration  time.Duration
}

func NewRateLimiter(dailyLimit, repeatLimit int, repeatWindow, blockDuration time.Duration) *RateLimiter {
	return &RateLimiter{
		dailyCounts:   make(map[uint][]time.Time),
		moduleCounts:  make(map[string][]time.Time),
		moduleBlocked: make(map[string]time.Time),
		dailyLimit:    dailyLimit,
		repeatLimit:   repeatLimit,
		repeatWindow:  repeatWindow,
		blockDuration: blockDuration,
	}
}

func (rl *RateLimiter) Middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID, exists := c.Get("userID")
		if !exists {
			c.Next()
			return
		}

		uid := userID.(uint)
		module := c.FullPath()
		key := fmt.Sprintf("%d|%s", uid, module)
		now := time.Now()

		rl.mu.Lock()
		defer rl.mu.Unlock()

		if rl.checkDailyLimit(c, uid, now) {
			return
		}

		if rl.checkModuleLimit(c, key, now) {
			return
		}

		c.Next()
	}
}

func (rl *RateLimiter) checkDailyLimit(c *gin.Context, uid uint, now time.Time) bool {
	startOfDay := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
	
	var validRequests []time.Time
	for _, t := range rl.dailyCounts[uid] {
		if t.After(startOfDay) {
			validRequests = append(validRequests, t)
		}
	}

	if len(validRequests) >= rl.dailyLimit {
		c.JSON(http.StatusTooManyRequests, gin.H{
			"error": "límite diario alcanzado",
			"reset": startOfDay.Add(24 * time.Hour).Format(time.RFC3339),
		})
		c.Abort()
		return true
	}

	rl.dailyCounts[uid] = append(validRequests, now)
	return false
}

func (rl *RateLimiter) checkModuleLimit(c *gin.Context, key string, now time.Time) bool {
	if blockedAt, blocked := rl.moduleBlocked[key]; blocked {
		if now.Sub(blockedAt) < rl.blockDuration {
			c.JSON(http.StatusTooManyRequests, gin.H{
				"error": "peticiones repetidas bloqueadas",
				"reset": blockedAt.Add(rl.blockDuration).Format(time.RFC3339),
			})
			c.Abort()
			return true
		}
		delete(rl.moduleBlocked, key)
	}

	cutoff := now.Add(-rl.repeatWindow)
	var validRequests []time.Time
	for _, t := range rl.moduleCounts[key] {
		if t.After(cutoff) {
			validRequests = append(validRequests, t)
		}
	}

	validRequests = append(validRequests, now)
	
	if len(validRequests) > rl.repeatLimit {
		rl.moduleBlocked[key] = now
		delete(rl.moduleCounts, key)
		c.JSON(http.StatusTooManyRequests, gin.H{
			"error": "demasiadas peticiones al mismo módulo",
			"reset": now.Add(rl.blockDuration).Format(time.RFC3339),
		})
		c.Abort()
		return true
	}

	rl.moduleCounts[key] = validRequests
	return false
}

func initializeApp() (*gin.Engine, error) {
	utils.LoadEnv()
	
	config.InitializeDatabase()
	
	if err := migrateDatabase(); err != nil {
		return nil, fmt.Errorf("database migration failed: %w", err)
	}

	services, err := initializeServices()
	if err != nil {
		return nil, fmt.Errorf("services initialization failed: %w", err)
	}

	controllers := initializeControllers(services)
	
	return setupRouter(controllers), nil
}

func migrateDatabase() error {
	return config.DB.AutoMigrate(
		&models.Usuarios{},
		&models.Role{},
		&models.UserUnblockCooldown{},
		&models.BiometricCredential{},
		&models.UserDeviceIP{},
		&models.LoginHistory{},
		&models.SecurityLog{},
		&models.PasswordHistory{},
		&models.UserRole{},
		&models.UserRelationship{},
		&models.PhotoChange{},
		&models.BannerChange{},
		&models.ProfileChange{},
		&models.OTP{},
		&models.OTPResend{},
		&models.Patient{},
		&models.TherapySession{},
		&models.TherapistRating{},
		&models.TherapistDisqualification{},
	)
}

func initializeServices() (*serviceContainer, error) {
	userService := services.NewUserService(config.DB)
	roleService := services.NewRoleService(config.DB)
	otpService := services.NewOTPService(config.DB)
	deviceIPRepo := services.NewDeviceIPRepo(config.DB)
	patientService := services.NewPatientService(config.DB)
	therapyService := services.NewTherapyService(config.DB)

	bioService, err := services.NewBioService(
		config.DB,
		userService,
		os.Getenv("RP_ORIGIN"),
		os.Getenv("RP_ID"),
		os.Getenv("RP_NAME"),
	)
	if err != nil {
		return nil, err
	}

	return &serviceContainer{
		user:    userService,
		role:    roleService,
		bio:     *bioService,
		otp:     otpService,
		deviceIP: *deviceIPRepo,
		patient: patientService,
		therapy: therapyService,
	}, nil
}

func initializeControllers(services *serviceContainer) *controllerContainer {
	return &controllerContainer{
		user:    controllers.NewUserController(services.user, services.otp, services.deviceIP),
		role:    controllers.NewRoleController(services.role),
		auth:    controllers.NewAuthController(),
		bio:     controllers.NewBioController(&services.bio),
		patient: controllers.NewPatientController(services.patient),
		therapy: controllers.NewTherapyController(services.therapy),
	}
}

func setupRouter(controllers *controllerContainer) *gin.Engine {
	r := routes.SetupRouter(
		controllers.user,
		controllers.role,
		controllers.auth,
		controllers.bio,
		controllers.patient,
		controllers.therapy,
	)

	rateLimiter := NewRateLimiter(50, 20, 20*time.Minute, 20*time.Minute)
	r.Use(rateLimiter.Middleware())
	r.MaxMultipartMemory = 8 << 20

	setupSwagger()
	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
	r.Static("/uploads", "./uploads")

	return r
}

func setupSwagger() {
	docs.SwaggerInfo.Title = "Mi API"
	docs.SwaggerInfo.Version = "1.0"
	docs.SwaggerInfo.BasePath = "/api"
}

func getPort() string {
	if port := os.Getenv("APP_PORT"); port != "" {
		return port
	}
	return "8080"
}

type serviceContainer struct {
	user     services.UserService
	role     services.RoleService
	bio      services.BioService
	otp      services.OTPService
	deviceIP services.DeviceIPRepo
	patient  *services.PatientService
	therapy  *services.TherapyService
}

type controllerContainer struct {
	user    *controllers.UserController
	role    *controllers.RoleController
	auth    *controllers.AuthController
	bio     *controllers.BioController
	patient *controllers.PatientController
	therapy *controllers.TherapyController
}

func main() {
	r, err := initializeApp()
	if err != nil {
		log.Fatalf("Failed to initialize application: %v", err)
	}

	// Arreglar serial_ids faltantes después de las migraciones
	if err := models.FixMissingSerialIDs(config.DB); err != nil {
		log.Printf("Warning: Could not fix missing serial IDs: %v", err)
		// No es fatal, continuamos
	}

	initialToken, err := utils.GenerateInitialAuthToken()
	if err != nil {
		log.Fatalf("Failed to generate initial token: %v", err)
	}
	fmt.Printf("Initial Token: %s\n", initialToken)

	if gin.Mode() == gin.DebugMode {
		generateDemoTokens()
	}

	port := getPort()
	log.Printf("Server starting on port %s", port)
	
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}

func generateDemoTokens() {
	demoUser := &models.Usuarios{
		ID:                1,
		Nombres_Apellidos: "Demo",
		Usuario:           "demo_user",
		Roles:             []models.Role{{Name: "admin"}},
	}

	token, refreshToken, err := utils.GenerateToken(demoUser)
	if err != nil {
		log.Printf("Warning: Failed to generate demo tokens: %v", err)
		return
	}

	fmt.Printf("Demo Token: %s\n", token)
	fmt.Printf("Demo Refresh Token: %s\n", refreshToken)
}