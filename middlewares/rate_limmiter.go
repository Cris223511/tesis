package middlewares

import (
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)


type RateLimiter struct {
    requests map[string][]time.Time
    mu       sync.Mutex
    limit    int
}

func NewRateLimiter(limit int) *RateLimiter {
    return &RateLimiter{
        requests: make(map[string][]time.Time),
        limit:    limit,
    }
}

func (rl *RateLimiter) Middleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        rl.mu.Lock()
        defer rl.mu.Unlock()

        // extrae IP y userID
        ip := c.ClientIP()
        var uidPart string
        if u, exists := c.Get("user_id"); exists {
            uidPart = fmt.Sprint(u)
        } else {
            uidPart = "anon"
        }
        key := ip + ":" + uidPart

        now := time.Now()
        startOfDay := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())

        // purga timestamps viejos
        if ts, ok := rl.requests[key]; ok {
            fresh := ts[:0]
            for _, t := range ts {
                if t.After(startOfDay) {
                    fresh = append(fresh, t)
                }
            }
            rl.requests[key] = fresh
        }

        // chequea límite
        if len(rl.requests[key]) >= rl.limit {
            c.JSON(http.StatusTooManyRequests, gin.H{
                "error":     "Límite diario de peticiones alcanzado (50/día)",
                "reset":     startOfDay.Add(24 * time.Hour).Format(time.RFC3339),
                "remaining": 0,
            })
            c.Abort()
            return
        }

        // registra la petición
        rl.requests[key] = append(rl.requests[key], now)

   
        c.Header("X-RateLimit-Limit", fmt.Sprint(rl.limit))
        c.Header("X-RateLimit-Remaining", fmt.Sprint(rl.limit-len(rl.requests[key])))
        c.Header("X-RateLimit-Reset", startOfDay.Add(24*time.Hour).Format(time.RFC3339))

        c.Next()
    }
}
