#!/bin/bash

echo "Starting ML Service entrypoint..."

# Intentar instalar pydantic-settings si no está disponible
pip install pydantic-settings 2>/dev/null || pip install pydantic==1.10.12

# Iniciar la aplicación
exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-5000}