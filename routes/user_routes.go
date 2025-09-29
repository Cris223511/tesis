package routes

import (
	"usuarios/controllers"
	"usuarios/middlewares"
	"usuarios/utils"
	"github.com/gin-gonic/gin"
)

func SetupRouter(
	userController *controllers.UserController,
	roleController *controllers.RoleController,
	authController *controllers.AuthController,
	bioController *controllers.BioController,
	patientController *controllers.PatientController,
	therapyController *controllers.TherapyController,
) *gin.Engine {

	r := gin.Default()
	r.Use(middlewares.CORSMiddleware())

	r.GET("/auth/gett", authController.GetToken)

	// ---------- RUTAS PÚBLICAS ----------
	public := r.Group("/api")
	{
		public.POST("/login", userController.Login)
		public.POST("/refresh-token", func(c *gin.Context) {
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

		public.POST("/otp/validate", userController.ValidateOTP)
		public.POST("/otp/resend", userController.ResendOTP)
		public.POST("/login/begin", bioController.BeginLogin)
		public.POST("/login/finish", bioController.FinishLogin)
		
		// ---------- CAMBIO DE CONTRASEÑA ----------
		public.POST("/password/validate-email", userController.ValidateEmailForPasswordChange)
		public.POST("/password/send-otp", userController.SendPasswordChangeOTP)
		public.POST("/password/verify-otp", userController.VerifyPasswordOTP)
		public.POST("/password/change-with-otp", userController.ChangePasswordWithOTP)


	}

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



		// ============== BIOMETRÍA ==============
		protected.GET("/capabilities", bioController.CheckBiometricCapabilities)
		protected.POST("/register/begin", bioController.BeginRegister)
		protected.POST("/register/finish", bioController.FinishRegister)
		protected.GET("/devices", bioController.ListDevices)
		protected.DELETE("/device", bioController.DeleteDevice)
		protected.PUT("/device/rename", bioController.RenameDevice)

		// ============== PACIENTES ==============
		protected.POST("/patients", patientController.CreatePatient)
		protected.GET("/patients", patientController.GetPatients)
		// Estadísticas de pacientes (debe ir antes de :id para evitar conflictos)
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
		protected.GET("/sessions/:id", therapyController.GetByID)
		protected.PUT("/sessions/:id", therapyController.Update)
		protected.PATCH("/sessions/:id/reschedule", therapyController.Reschedule)
		protected.DELETE("/sessions/:id", therapyController.Delete)
		protected.GET("/sessions/:id/export/pdf", therapyController.ExportToPDF)
		protected.GET("/sessions/:id/export/jpg", therapyController.ExportToJPG)

		// ============== CALIFICACIONES DE TERAPEUTAS ==============
		protected.POST("/therapist-ratings", therapyController.CreateTherapistRating)
		protected.GET("/therapist-ratings/:therapist_id", therapyController.GetTherapistRatings)
		protected.GET("/sessions/rating/:session_id", therapyController.GetSessionRating)
	}

	return r
}