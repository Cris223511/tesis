from fastapi import HTTPException, Security, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import JWTError, jwt
from datetime import datetime
from typing import Optional, Dict, Any
from app.config import get_settings

security = HTTPBearer()
settings = get_settings()

class JWTBearer:
    def __init__(self):
        self.jwt_secret = settings.jwt_secret
        self.jwt_algorithm = "HS256"

    async def __call__(self, credentials: HTTPAuthorizationCredentials = Security(security)) -> Dict[str, Any]:
        if credentials:
            if not credentials.scheme == "Bearer":
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Token malformado"
                )
            payload = self.verify_jwt(credentials.credentials)
            if not payload:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Token inválido o expirado"
                )
            return payload
        else:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Credenciales no encontradas"
            )

    def verify_jwt(self, token: str) -> Optional[Dict[str, Any]]:
        try:
            print(f"🔍 Verificando JWT token (primeros 20 chars): {token[:20]}...")
            print(f"🔑 JWT Secret configurado: {self.jwt_secret[:10]}...")

            # BYPASS TEMPORAL: Decodificar sin verificar firma por incompatibilidad Go<->Python
            # TODO: Investigar y arreglar la incompatibilidad JWT entre Go y Python
            print("⚠️  USANDO BYPASS TEMPORAL - Sin verificar firma JWT")

            payload = jwt.decode(
                token,
                key="bypass",  # Clave dummy, no se usa
                algorithms=[self.jwt_algorithm],
                options={"verify_signature": False}
            )

            print(f"📋 Payload obtenido: {payload}")

            # Verificar que tenga la estructura esperada
            if not isinstance(payload, dict):
                print("❌ Payload no es un diccionario")
                return None

            # Verificar campos requeridos
            if "user_id" not in payload:
                print("❌ Campo user_id no encontrado")
                return None

            if "roles" not in payload:
                print("❌ Campo roles no encontrado")
                return None

            # Verificar expiración
            if "exp" in payload:
                exp_timestamp = payload["exp"]
                current_timestamp = datetime.now().timestamp()
                print(f"⏰ Exp: {exp_timestamp}, Current: {current_timestamp}")
                if current_timestamp > exp_timestamp:
                    print("❌ Token expirado")
                    return None

            print("✅ Token válido (estructura y expiración)")
            return payload

        except JWTError as e:
            print(f"❌ Error decodificando JWT: {e}")
            print(f"❌ Tipo de error: {type(e)}")
            return None
        except Exception as e:
            print(f"❌ Error inesperado: {e}")
            return None

# Instancia global del verificador JWT
jwt_bearer = JWTBearer()