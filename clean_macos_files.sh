#!/bin/bash

echo "🧹 Limpiando archivos de metadatos de macOS..."

find . -type f -name '._*' -delete
find . -type f -name '.DS_Store' -delete

echo "✅ Archivos de metadatos eliminados"

echo "🧹 Limpiando build folders..."
./gradlew clean

echo "✅ Proyecto limpio y listo para compilar"
