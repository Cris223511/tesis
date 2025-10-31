package main

import (
	"context"
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
	"github.com/redis/go-redis/v9"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)







var redisClient *redis.Client

type RateLimiter struct {
	mu             sync.Mutex
	dailyCounts    map[uint][]time.Time
	moduleCounts   map[string][]time.Time
	moduleBlocked  map[string]time.Time
	dailyLimit     int
	repeatLimit    int
	repeatWindow   time.Duration
	blockDuration  time.Duration
	useRedis       bool 
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
		useRedis:      redisClient != nil, // Usar Redis si está disponible
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

	// Si Redis está disponible, usarlo
	if rl.useRedis && redisClient != nil {
		ctx := context.Background()
		key := fmt.Sprintf("rate_limit:daily:%d", uid)

		current, err := redisClient.Get(ctx, key).Int()
		if err == redis.Nil {
			// Primera request del día
			redisClient.Set(ctx, key, 1, 24*time.Hour)
			return false
		} else if err != nil {
			// Error de Redis, fallback a memoria
			log.Printf("Redis error, fallback: %v", err)
		} else {
			// Redis OK
			if current >= rl.dailyLimit {
				c.JSON(http.StatusTooManyRequests, gin.H{
					"error": "límite diario alcanzado (Redis)",
					"reset": startOfDay.Add(24 * time.Hour).Format(time.RFC3339),
				})
				c.Abort()
				return true
			}
			redisClient.Incr(ctx, key)
			return false
		}
	}

	// Código original - memoria
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

	// Inicializar Redis (opcional)
	initRedis()

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

// initRedis inicializa Redis de manera opcional
func initRedis() {
	redisHost := os.Getenv("REDIS_HOST")
	if redisHost == "" {
		redisHost = "localhost"
	}

	redisPort := os.Getenv("REDIS_PORT")
	if redisPort == "" {
		redisPort = "6379"
	}

	redisClient = redis.NewClient(&redis.Options{
		Addr:     fmt.Sprintf("%s:%s", redisHost, redisPort),
		Password: os.Getenv("REDIS_PASSWORD"),
		DB:       0,
	})

	// Probar conexión
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, err := redisClient.Ping(ctx).Result()
	if err != nil {
		log.Printf("⚠️  Redis no disponible: %v (continuando sin cache)", err)
		redisClient = nil
		return
	}

	log.Printf("✅ Redis conectado: %s:%s", redisHost, redisPort)
}

func migrateDatabase() error {
	return config.DB.AutoMigrate(
		&models.Usuarios{},
		&models.Role{},
		&models.UserUnblockCooldown{},
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
	adminService := services.NewAdminService(config.DB)

	// Initialize automatic session state updater
	log.Printf("🔄 Inicializando actualizador automático de sesiones...")
	therapyService.StartAutomaticSessionUpdater()

	return &serviceContainer{
		user:     userService,
		role:     roleService,
		otp:      otpService,
		deviceIP: *deviceIPRepo,
		patient:  patientService,
		therapy:  therapyService,
		admin:    adminService,
	}, nil
}

func initializeControllers(services *serviceContainer) *controllerContainer {
	return &controllerContainer{
		user:    controllers.NewUserController(services.user, services.otp, services.deviceIP),
		role:    controllers.NewRoleController(services.role),
		auth:    controllers.NewAuthController(services.user, services.otp, config.DB),
		patient: controllers.NewPatientController(services.patient),
		therapy: controllers.NewTherapyController(services.therapy),
		admin:   controllers.NewAdminController(services.admin),
	}
}

func setupRouter(controllers *controllerContainer) *gin.Engine {
	r := routes.SetupRouter(
		controllers.user,
		controllers.role,
		controllers.auth,
		controllers.patient,
		controllers.therapy,
		controllers.admin,
	)

	rateLimiter := NewRateLimiter(50, 20, 20*time.Minute, 20*time.Minute)
	r.Use(rateLimiter.Middleware())
	r.MaxMultipartMemory = 8 << 20

	setupSwagger()
	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
	r.Static("/uploads", "./uploads")

	// Health check endpoint para Docker
	r.GET("/health", func(c *gin.Context) {
		status := "ok"
		details := gin.H{
			"database": "connected",
			"redis":    "not configured",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
		}

		// Verificar conexión a base de datos
		sqlDB, err := config.DB.DB()
		if err != nil || sqlDB.Ping() != nil {
			details["database"] = "disconnected"
			status = "error"
		}

		// Verificar Redis si está configurado
		if redisClient != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			if err := redisClient.Ping(ctx).Err(); err != nil {
				details["redis"] = "disconnected"
				status = "warning"
			} else {
				details["redis"] = "connected"
			}
		}

		if status == "error" {
			c.JSON(http.StatusServiceUnavailable, gin.H{
				"status": status,
				"details": details,
			})
		} else {
			c.JSON(http.StatusOK, gin.H{
				"status": status,
				"details": details,
			})
		}
	})

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
	otp      services.OTPService
	deviceIP services.DeviceIPRepo
	patient  *services.PatientService
	therapy  *services.TherapyService
	admin    *services.AdminService
}

type controllerContainer struct {
	user    *controllers.UserController
	role    *controllers.RoleController
	auth    *controllers.AuthController
	patient *controllers.PatientController
	therapy *controllers.TherapyController
	admin   *controllers.AdminController
}

func main() {
	r, err := initializeApp()
	if err != nil {
		log.Fatalf("Failed to initialize application: %v", err)
	}

	// Cerrar conexiones cuando la aplicación termine
	defer func() {
		if redisClient != nil {
			redisClient.Close()
		}
		log.Println("Application shutdown complete")
	}()

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
	log.Printf("Redis connection: %s:%s", os.Getenv("REDIS_HOST"), os.Getenv("REDIS_PORT"))

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