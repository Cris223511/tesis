#!/bin/bash

echo "Starting ML Service entrypoint..."
echo "Python version: $(python --version)"
echo "Working directory: $(pwd)"
echo "PORT: ${PORT:-5000}"

# Verificar y corregir imports de pydantic
if ! python -c "import pydantic_settings" 2>/dev/null; then
    echo "pydantic_settings not found, using compatibility mode..."
    # Modificar el archivo config.py dinámicamente
    sed -i '1s/from pydantic_settings import BaseSettings/from pydantic import BaseSettings/' /app/app/config.py
    echo "Modified config.py to use pydantic.BaseSettings"
fi

# Iniciar la aplicación
exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-5000}