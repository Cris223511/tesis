try:
    # Para Pydantic v2
    from pydantic_settings import BaseSettings
except ImportError:
    # Para Pydantic v1
    from pydantic import BaseSettings

from pydantic import Field
from typing import List
from functools import lru_cache
import os

class Settings(BaseSettings):

    # Database
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = ""
    db_user: str = ""
    db_password: str = ""

    # Redis cache
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0

    # JWT
    jwt_secret: str = ""

    # ML Model
    model_path: str = "models/emotion_model.h5"
    max_batch_size: int = 32
    model_pool_size: int = 3

    # Service config - PORT del .env se lee como 'port'
    port: int = Field(5000, alias='PORT')  # Acepta PORT del .env
    service_port: int = 5000  # Para compatibilidad con el código existente
    java_service_url: str = "http://localhost:8080"

    # Limitaciones de uso
    max_requests_per_day: int = 100
    max_edits_per_day: int = 2

    emotions: List[str] = ["angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"]
    bias_correction: List[float] = [1.1, 0.9, 0.95, 1.2, 1.05, 0.95, 1.0]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        # Sincronizar port con service_port
        if hasattr(self, 'port'):
            self.service_port = self.port

    class Config:
        env_file = ".env"
        env_file_encoding = 'utf-8'
        extra = 'allow'  # Permitir campos extras del .env

@lru_cache()
def get_settings():
    return Settings()

settings = Settings()