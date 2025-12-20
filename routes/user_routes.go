package routes

import (
	"log"
	"usuarios/controllers"
	"usuarios/middlewares"
	"usuarios/utils"
	"github.com/gin-gonic/gin"
)

func SetupRouter(
	userController *controllers.UserController,
	roleController *controllers.RoleController,
	authController *controllers.AuthController,
	patientController *controllers.PatientController,
	therapyController *controllers.TherapyController,
	adminController *controllers.AdminController,
	versionController *controllers.VersionController,
) *gin.Engine {

	r := gin.Default()


	r.Use(func(c *gin.Context) {
		if c.Request.URL.Path == "/api/otp/validate" {
			log.Printf("[CUSTOM_LOG] OTP/validate request received!")
			log.Printf("[CUSTOM_LOG] Method: %s, ContentType: %s, ContentLength: %d",
				c.Request.Method, c.Request.Header.Get("Content-Type"), c.Request.ContentLength)
		}
		c.Next()
	})

	r.Use(middlewares.CORSMiddleware())

	r.GET("/auth/gett", authController.GetToken)

	// ---------- RUTAS PÚBLICAS ----------
	r.POST("/api/login", userController.Login)
	r.POST("/api/otp/validate", authController.VerifyOTP)
	r.POST("/api/otp/resend", authController.ResendOTP)
	r.POST("/api/refresh-token", func(c *gin.Context) {
		var req struct {
			RefreshToken string `json:"refresh_token"`
		}
		if err := c.ShouldBindJSON(&req); err != nil || req.RefreshToken == "" {
			c.JSON(400, gin.H{"error": "refresh_token faltante"})
			return
		}

		newTok, newRef, err := utils.RefreshToken(req.RefreshToken)
		if err != nil {
			c.JSON(401, gin.H{"error": err.Error()})
			return
		}
		c.JSON(200, gin.H{
			"bearer_token":  newTok,
			"refresh_token": newRef,
		})
	})


	r.GET("/api/app/version", versionController.CheckVersion)

	// ---------- CAMBIO DE CONTRASEÑA ----------
	r.POST("/api/password/validate-email", userController.ValidateEmailForPasswordChange)
	r.POST("/api/password/send-otp", userController.SendPasswordChangeOTP)
	r.POST("/api/password/verify-otp", userController.VerifyPasswordOTP)
	r.POST("/api/password/change-with-otp", userController.ChangePasswordWithOTP)

	// ---------- RUTAS PROTEGIDAS ----------
	protected := r.Group("/api")
	protected.Use(middlewares.AuthMiddleware())
	{
		// ============== USUARIOS ==============
		protected.GET("/users", userController.List)
		protected.POST("/register", userController.Register)
		protected.GET("/users/search", userController.Search)
		protected.PUT("/users/:id", userController.Update)
		protected.DELETE("/users/:id", userController.Delete)
		protected.PUT("/users/:id/password", userController.UpdatePassword)
		protected.PATCH("/users/:id/status", userController.ChangeAccountStatus)
		protected.POST("/users/:id/unlock", userController.UnlockAccount)
		protected.GET("/users/:id/login-attempts", userController.GetLoginAttempts)
		protected.GET("/users/:id", userController.GetByID)
		
		protected.GET("/profile", userController.GetUserProfile)
		protected.GET("/profile/:id", userController.GetUserProfile)
		protected.GET("/profile/children", userController.GetUserChildren)
		protected.PUT("/profile/update", userController.UpdateProfile)
		protected.GET("/profile/changes", userController.GetProfileChanges) 
		
		protected.POST("/users/photo", userController.UploadPhoto)
		protected.GET("/users/photo/changes", userController.GetPhotoChanges)
		
		protected.POST("/users/banner", userController.UploadBanner)
		protected.GET("/users/banner/changes", userController.GetBannerChanges)

		// ============== ROLES ==============
		protected.POST("/roles", roleController.CreateRole)
		protected.GET("/roles", roleController.GetAllRoles)
		protected.PUT("/roles/:id", roleController.UpdateRole)
		protected.DELETE("/roles/:id", roleController.DeleteRole)
		// ============== PACIENTES ==============
		protected.POST("/patients", patientController.CreatePatient)
		protected.GET("/patients", patientController.GetPatients)
		
		protected.GET("/patients/:id/stats", therapyController.GetPatientStats)
		protected.GET("/patients/:id", patientController.GetPatient)
		protected.PUT("/patients/:id", patientController.UpdatePatient)
		protected.DELETE("/patients/:id", patientController.DeletePatient)

		// ============== SESIONES TERAPÉUTICAS ==============
		protected.POST("/sessions", therapyController.Create)
		protected.GET("/sessions", therapyController.GetAll)
		protected.GET("/sessions/paginated", therapyController.GetPaginated)
		protected.GET("/sessions/latest-patients", therapyController.GetLatestPatients)
		protected.GET("/sessions/available-therapists", therapyController.GetAvailableTherapists)
		protected.GET("/sessions/patient/:patient_id", therapyController.GetPatientSessions) 
		protected.GET("/sessions/:id", therapyController.GetByID)
		protected.PUT("/sessions/:id", therapyController.Update)
		protected.PUT("/sessions/:id/status", therapyController.UpdateSessionStatus)
		protected.PATCH("/sessions/:id/reschedule", therapyController.Reschedule)
		protected.DELETE("/sessions/:id", therapyController.Delete)
		protected.GET("/sessions/:id/export/pdf", therapyController.ExportToPDF)
		protected.GET("/sessions/:id/export/jpg", therapyController.ExportToJPG)
		protected.POST("/sessions/update-expired", therapyController.UpdateExpiredSessions)

		// ============== CALIFICACIONES DE TERAPEUTAS ==============
		protected.POST("/therapist-ratings", therapyController.CreateTherapistRating)
		protected.GET("/therapist-ratings/:therapist_id", therapyController.GetTherapistRatings)
		protected.GET("/sessions/rating/:session_id", therapyController.GetSessionRating)

		// ============== HISTORIAL DE REPORTES ==============
		protected.GET("/therapy/patient/:patient_id/reports", therapyController.GetPatientReportHistory)
		protected.POST("/therapy/patient/:patient_id/reports/generate", therapyController.GetPatientFullReport)
		protected.DELETE("/therapy/patient/:patient_id/reports/:report_id", therapyController.DeletePatientReport)

		// ============== ADMINISTRACIÓN ==============
		protected.GET("/admin/stats", adminController.GetAdminStats)
		protected.GET("/admin/therapists", adminController.GetTherapists)
		protected.GET("/admin/sessions/stats", adminController.GetSessionStats)
	}

	return r
}