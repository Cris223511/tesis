from pydantic_settings import BaseSettings
from typing import List
from functools import lru_cache
import os

class Settings(BaseSettings):

    db_host: str = os.getenv("DB_HOST", "localhost")
    db_port: int = int(os.getenv("DB_PORT", 3306))
    db_name: str = os.getenv("DB_NAME", "")
    db_user: str = os.getenv("DB_USER", "")
    db_password: str = os.getenv("DB_PASSWORD", "")

    # Redis cache
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", 6379))
    redis_db: int = 0

    jwt_secret: str = os.getenv("JWT_SECRET", "")

    # ML Model 
    model_path: str = os.getenv("MODEL_PATH", "models/emotion_model.h5")
    max_batch_size: int = int(os.getenv("MAX_BATCH_SIZE", 32))
    model_pool_size: int = int(os.getenv("MODEL_POOL_SIZE", 3))

    service_port: int = int(os.getenv("PORT", 5000))
    java_service_url: str = os.getenv("JAVA_SERVICE_URL", "http://localhost:8080")

    # Limitaciones de uso
    max_requests_per_day: int = 100
    max_edits_per_day: int = 2

  
    emotions: List[str] = ["angry", "disgust", "fear", "happy", "neutral", "sad", "surprise"]
    bias_correction: List[float] = [1.1, 0.9, 0.95, 1.2, 1.05, 0.95, 1.0]

    class Config:
        env_file = ".env"

@lru_cache()
def get_settings():
    return Settings()

settings = Settings()