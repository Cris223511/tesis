package middlewares

import (
	"bytes"
	"io"
	"log"

	"github.com/gin-gonic/gin"
)


func DebugMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		log.Printf("[DEBUG] === REQUEST START ===")
		log.Printf("[DEBUG] Method: %s", c.Request.Method)
		log.Printf("[DEBUG] Path: %s", c.Request.URL.Path)
		log.Printf("[DEBUG] URL: %s", c.Request.URL.String())
		log.Printf("[DEBUG] Content-Type: %s", c.Request.Header.Get("Content-Type"))
		log.Printf("[DEBUG] Content-Length: %d", c.Request.ContentLength)
		log.Printf("[DEBUG] RemoteAddr: %s", c.Request.RemoteAddr)

		// Log headers
		for key, values := range c.Request.Header {
			for _, value := range values {
				log.Printf("[DEBUG] Header %s: %s", key, value)
			}
		}

		// Try to read body
		if c.Request.Body != nil && c.Request.ContentLength > 0 {
			bodyBytes, err := io.ReadAll(c.Request.Body)
			if err == nil {
				log.Printf("[DEBUG] Body (%d bytes): %s", len(bodyBytes), string(bodyBytes))
				// Reset body for handler to read
				c.Request.Body = io.NopCloser(bytes.NewBuffer(bodyBytes))
			}
		}

		c.Next()

		log.Printf("[DEBUG] === REQUEST END ===")
		log.Printf("[DEBUG] Status Code: %d", c.Writer.Status())
	}
}