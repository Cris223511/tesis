package middlewares

import (
	"bytes"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

func RequestLoggingMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Log request details
		startTime := time.Now()
		path := c.Request.URL.Path
		method := c.Request.Method
		contentType := c.Request.Header.Get("Content-Type")
		contentLength := c.Request.ContentLength

		log.Printf("[REQUEST_START] %s %s | Content-Type: %s | Content-Length: %d", method, path, contentType, contentLength)

		// Try to read body if POST/PUT/PATCH
		if c.Request.Method == http.MethodPost || c.Request.Method == http.MethodPut || c.Request.Method == http.MethodPatch {
			if c.Request.Body != nil {
				bodyBytes, err := io.ReadAll(c.Request.Body)
				if err == nil {
					log.Printf("[REQUEST_BODY] %s | Body: %s", path, string(bodyBytes))
					// Reset body for handler to read
					c.Request.Body = io.NopCloser(bytes.NewBuffer(bodyBytes))
				}
			}
		}

		c.Next()

		duration := time.Since(startTime)
		statusCode := c.Writer.Status()

		log.Printf("[REQUEST_END] %s %s | Status: %d | Duration: %v", method, path, statusCode, duration)
	}
}
