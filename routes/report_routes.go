package routes

import (
	"usuarios/controllers"
	"usuarios/middlewares"

	"github.com/gin-gonic/gin"
)

func SetupReportRoutes(router *gin.Engine, reportController *controllers.ReportController) {
	api := router.Group("/api")
	{
		reports := api.Group("/reports")
		reports.Use(middlewares.AuthMiddleware())
		{
			reports.POST("/", reportController.Create)
			reports.GET("/", reportController.List)
			reports.GET("/statistics", reportController.GetStatistics)
			reports.GET("/:id", reportController.GetByID)
			reports.PUT("/:id", reportController.Update)
			reports.DELETE("/:id", reportController.Delete)
			reports.GET("/:id/download", reportController.Download)

			reports.POST("/session/:sessionId/generate", reportController.GenerateSessionReport)
			reports.POST("/general/generate", reportController.GenerateGeneralReport)
		}
	}
}