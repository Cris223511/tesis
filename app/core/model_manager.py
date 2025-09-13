import tensorflow as tf
import asyncio
from typing import List
import numpy as np

class ModelPool:
    def __init__(self, model_path: str, pool_size: int = 3):
        self.models = []
        self.available = asyncio.Queue()
        
        for _ in range(pool_size):
            model = tf.keras.models.load_model(model_path)
            self.models.append(model)
            self.available.put_nowait(model)
    
    async def get_model(self):
        return await self.available.get()
    
    async def return_model(self, model):
        await self.available.put(model)