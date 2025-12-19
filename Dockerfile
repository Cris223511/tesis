# Usar imagen oficial de Go como base
FROM golang:1.23-alpine AS builder

# Instalar dependencias del sistema
RUN apk add --no-cache git ca-certificates tzdata

# Establecer directorio de trabajo
WORKDIR /app

# Copiar archivos de dependencias
COPY go.mod go.sum ./

# Descargar dependencias
RUN go mod download

# Copiar código fuente
COPY . .

# Compilar la aplicación
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# Etapa final - imagen mínima
FROM alpine:latest

# Instalar ca-certificates para HTTPS y wget para health check
RUN apk --no-cache add ca-certificates tzdata wget curl

# Crear directorio de trabajo
WORKDIR /app

# Copiar el binario compilado
COPY --from=builder /app/main .

# Crear directorios necesarios
RUN mkdir -p /app/public /app/uploads /app/logs

# Copiar archivos públicos si existen
COPY --from=builder /app/public/ ./public/

# Exponer puerto
EXPOSE 8080

# Variables de entorno por defecto
ENV GIN_MODE=release
ENV PORT=8080

# Health check más tolerante para inicio
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Comando para ejecutar la aplicación
CMD ["./main"]

# Labels para metadatos
LABEL maintainer="Backend Team"
LABEL version="1.0.1"
LABEL description="servicios de gestion de usuarios con autentacion basada en JWT"