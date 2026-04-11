# Usar imagen oficial de Go como base
FROM --platform=linux/amd64 golang:1.23-alpine AS builder

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
FROM --platform=linux/amd64 alpine:latest

# Instalar ca-certificates para HTTPS
RUN apk --no-cache add ca-certificates tzdata

# Crear directorio de trabajo
WORKDIR /root/

# Copiar el binario compilado
COPY --from=builder /app/main .

# Copiar archivos de configuración si existen
COPY --from=builder /app/.env* ./

# Exponer puerto
EXPOSE 8080

# Variables de entorno por defecto
ENV GIN_MODE=release
ENV PORT=8080

# Comando de health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Comando para ejecutar la aplicación
CMD ["./main"]

# Labels para metadatos
LABEL maintainer="Backend Team"
LABEL version="1.0.0"
LABEL description="Backend de usuarios con autenticación JWT y roles"