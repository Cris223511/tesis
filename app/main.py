from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.api import routes
from app.config import get_settings
import uvicorn
from datetime import datetime
from dotenv import load_dotenv
import os

# Cargar variables de entorno
load_dotenv()

settings = get_settings()

# Crear aplicaci�n FastAPI
app = FastAPI(
    title="Emotion ML Service",
    description="Servicio de an�lisis de emociones con ML protegido por JWT",
    version="1.0.0"
)

# Configurar CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Incluir rutas
app.include_router(routes.router, prefix="/api/v1")

# Manejador global de excepciones
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={
            "error": "Error interno del servidor",
            "detail": str(exc),
            "timestamp": datetime.now().isoformat()
        }
    )

@app.get("/")
async def root():
    return {
        "service": "Emotion ML Service",
        "status": "running",
        "version": "1.0.0",
        "endpoints": {
            "health": "/api/v1/health",
            "analyze": "/api/v1/analyze-emotion (requires auth)",
            "docs": "/docs"
        }
    }

@app.on_event("startup")
async def startup_event():
    print(f"""
    ========================================
    Emotion ML Service iniciado
    ========================================
    Puerto: {settings.service_port}
    Redis: {settings.redis_host}:{settings.redis_port}
    Max peticiones/d�a: {settings.max_requests_per_day}
    JWT Secret configurado: {'S�' if settings.jwt_secret else 'No'}
    ========================================
    """)

    # Verificar que el JWT secret est� configurado
    if not settings.jwt_secret:
        print("�  ADVERTENCIA: JWT_SECRET no est� configurado en .env")
        print("�  El servicio no podr� validar tokens correctamente")

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.service_port,
        reload=True,
        log_level="info"
    )