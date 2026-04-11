# syntax=docker/dockerfile:1
# Usar imagen base oficial de Python en linux/amd64
# Force rebuild: 2026-04-08-03
FROM --platform=linux/amd64 python:3.11.7-slim-bullseye

# Establecer directorio de trabajo
WORKDIR /app

# Instalar solo dependencias esenciales
RUN apt-get update && apt-get install -y \
    curl \
    libglib2.0-0 \
    libgl1 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copiar requirements.txt primero para aprovechar el cache de Docker
COPY requirements.txt .

# Instalar dependencias de Python
RUN pip install --no-cache-dir --upgrade pip
RUN pip install --no-cache-dir -r requirements.txt

# Copiar el código de la aplicación
COPY . .

# Crear directorio para modelos
RUN mkdir -p models

# Crear directorio para logs
RUN mkdir -p logs

# Establecer permisos correctos
RUN chmod -R 755 /app

# Variables de entorno por defecto
ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1

# Exponer puerto
EXPOSE 5000

# Comando de health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

# Copiar y hacer ejecutable el entrypoint
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

# Comando por defecto para ejecutar la aplicación
CMD ["./entrypoint.sh"]

# Labels para metadatos
LABEL maintainer="Emotion ML Team"
LABEL version="1.0.0"
LABEL description="Servicio de análisis de emociones con ML y JWT"
