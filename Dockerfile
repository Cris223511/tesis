# Usar imagen base oficial de Python
# Force rebuild: 2026-04-08-01
FROM python:3.11-slim

# Establecer directorio de trabajo
WORKDIR /app

# Instalar dependencias del sistema necesarias para OpenCV y ML
RUN apt-get update && apt-get install -y \
    curl \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    libgtk-3-0 \
    libavcodec-dev \
    libavformat-dev \
    libswscale-dev \
    libv4l-dev \
    libatlas-base-dev \
    gfortran \
    libjpeg-dev \
    libpng-dev \
    libtiff-dev \
    libdc1394-22 \
    pkg-config \
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