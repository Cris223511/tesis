import redis
import json
from typing import Optional

class CacheService:
    def __init__(self, host: str, port: int):
        self.redis_client = redis.Redis(host=host, port=port, decode_responses=True)
    
    async def get(self, key: str) -> Optional[dict]:
        try:
            cached = self.redis_client.get(key)
            if cached:
                return json.loads(cached)
        except Exception:
            pass
        return None
    
    async def set(self, key: str, value: dict, ttl: int = 3600):
        try:
            self.redis_client.setex(key, ttl, json.dumps(value))
        except Exception:
            pass