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
	}

	return r
}