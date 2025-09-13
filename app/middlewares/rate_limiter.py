import redis
from fastapi import HTTPException, status
from datetime import datetime, timedelta
from typing import Optional, List
from app.config import get_settings

settings = get_settings()

class RateLimiter:
    def __init__(self):
        self.redis_client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            decode_responses=True
        )
        self.max_requests = settings.max_requests_per_day
        self.max_edits_padre = 2  # Máximo de ediciones para rol padre

    def is_admin(self, roles: str) -> bool:
        """Verifica si el usuario tiene rol admin"""
        if not roles:
            return False
        role_list = roles.split(",") if "," in roles else [roles]
        return "AD" in role_list

    def is_padre(self, roles: str) -> bool:
        """Verifica si el usuario tiene rol padre"""
        if not roles:
            return False
        role_list = roles.split(",") if "," in roles else [roles]
        return "PD" in role_list

    async def check_rate_limit(self, user_id: int, client_ip: str, roles: str = "") -> dict:
        """
        Verifica el límite de peticiones para un usuario
        Los admins no tienen límite
        """
        # Los admins no tienen límites
        if self.is_admin(roles):
            return {
                "limit": -1,  # Sin límite
                "remaining": -1,
                "reset": None,
                "current": 0,
                "is_admin": True
            }

        # Crear clave única por usuario y día
        today = datetime.now().strftime("%Y-%m-%d")
        key = f"rate_limit:{user_id}:{client_ip}:{today}"

        try:
            # Obtener contador actual
            current_count = self.redis_client.get(key)

            if current_count is None:
                # Primera petición del día
                self.redis_client.setex(
                    key,
                    timedelta(days=1),
                    1
                )
                remaining = self.max_requests - 1
                current_count = 1
            else:
                current_count = int(current_count)

                if current_count >= self.max_requests:
                    # Límite alcanzado
                    reset_time = datetime.now().replace(
                        hour=0, minute=0, second=0, microsecond=0
                    ) + timedelta(days=1)

                    raise HTTPException(
                        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                        detail={
                            "error": f"Límite diario de peticiones alcanzado ({self.max_requests}/día)",
                            "reset": reset_time.isoformat(),
                            "remaining": 0
                        }
                    )

                # Incrementar contador
                new_count = self.redis_client.incr(key)
                remaining = self.max_requests - new_count
                current_count = new_count

            # Calcular tiempo de reset
            reset_time = datetime.now().replace(
                hour=0, minute=0, second=0, microsecond=0
            ) + timedelta(days=1)

            return {
                "limit": self.max_requests,
                "remaining": remaining,
                "reset": reset_time.isoformat(),
                "current": current_count,
                "is_admin": False
            }

        except redis.RedisError:
            # Si Redis falla, permitir la petición pero registrar el error
            print("Error conectando con Redis para rate limiting")
            return {
                "limit": self.max_requests,
                "remaining": self.max_requests,
                "reset": None,
                "current": 0,
                "is_admin": False
            }

    async def check_edit_limit(self, user_id: int, roles: str) -> dict:
        """
        Verifica el límite de ediciones para usuarios con rol padre
        Los admins no tienen límite de ediciones
        """
        # Los admins pueden editar sin límite
        if self.is_admin(roles):
            return {
                "can_edit": True,
                "edits_remaining": -1,
                "edits_today": 0,
                "max_edits": -1
            }

        # Si no es padre, no puede editar
        if not self.is_padre(roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No tienes permisos para editar"
            )

        # Verificar límite de ediciones para rol padre
        today = datetime.now().strftime("%Y-%m-%d")
        edit_key = f"edits:{user_id}:{today}"

        try:
            current_edits = self.redis_client.get(edit_key)

            if current_edits is None:
                current_edits = 0
            else:
                current_edits = int(current_edits)

            if current_edits >= self.max_edits_padre:
                raise HTTPException(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    detail={
                        "error": f"Límite de ediciones alcanzado ({self.max_edits_padre}/día)",
                        "edits_today": current_edits,
                        "max_edits": self.max_edits_padre
                    }
                )

            return {
                "can_edit": True,
                "edits_remaining": self.max_edits_padre - current_edits,
                "edits_today": current_edits,
                "max_edits": self.max_edits_padre
            }

        except redis.RedisError:
            print("Error conectando con Redis para límite de ediciones")
            return {
                "can_edit": True,
                "edits_remaining": self.max_edits_padre,
                "edits_today": 0,
                "max_edits": self.max_edits_padre
            }

    async def increment_edit_counter(self, user_id: int, roles: str):
        """Incrementa el contador de ediciones para un usuario"""
        if self.is_admin(roles):
            return  # No contar ediciones para admins

        today = datetime.now().strftime("%Y-%m-%d")
        edit_key = f"edits:{user_id}:{today}"

        try:
            self.redis_client.incr(edit_key)
            self.redis_client.expire(edit_key, timedelta(days=1))
        except redis.RedisError:
            print("Error incrementando contador de ediciones")

# Instancia global del rate limiter
rate_limiter = RateLimiter()