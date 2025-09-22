#!/usr/bin/env python3
"""
Script de prueba para Redis con ejemplos de uso comunes
Asegúrate de tener Redis corriendo: redis-server
"""

import redis
import json
import time
from datetime import datetime, timedelta

# Conectar a Redis
client = redis.Redis(
    host='localhost',
    port=6379,
    decode_responses=True,  # Para recibir strings en vez de bytes
    db=0
)

def test_connection():
    """Probar conexión a Redis"""
    print("1. PROBANDO CONEXIÓN")
    print("-" * 40)

    try:
        response = client.ping()
        print(f"✅ Conexión exitosa: {response}")

        # Info del servidor
        info = client.info('server')
        print(f"📊 Redis version: {info['redis_version']}")
        print(f"📊 Uptime: {info['uptime_in_seconds']} segundos")
    except redis.ConnectionError:
        print("❌ Error: No se puede conectar a Redis")
        print("   Asegúrate de que Redis esté corriendo: redis-server")
        return False
    print()
    return True

def basic_operations():
    """Operaciones básicas con strings"""
    print("2. OPERACIONES BÁSICAS")
    print("-" * 40)

    # SET y GET
    client.set('usuario:1:nombre', 'Juan Pérez')
    nombre = client.get('usuario:1:nombre')
    print(f"✅ SET/GET: {nombre}")

    # SET con expiración (TTL)
    client.setex('token:abc123', 3600, 'valor_secreto')  # Expira en 1 hora
    ttl = client.ttl('token:abc123')
    print(f"✅ Token guardado, expira en: {ttl} segundos")

    # Incrementar contador
    client.set('visitas', 0)
    client.incr('visitas')
    client.incr('visitas')
    visitas = client.get('visitas')
    print(f"✅ Contador de visitas: {visitas}")

    # EXISTS - verificar si existe una clave
    existe = client.exists('usuario:1:nombre')
    print(f"✅ ¿Existe 'usuario:1:nombre'?: {bool(existe)}")

    print()

def work_with_json():
    """Guardar y recuperar objetos JSON"""
    print("3. TRABAJANDO CON JSON")
    print("-" * 40)

    # Objeto complejo
    analysis_result = {
        'id': 'analysis_123',
        'user_id': 1,
        'timestamp': datetime.now().isoformat(),
        'emotions': {
            'happy': 0.85,
            'sad': 0.10,
            'neutral': 0.05
        },
        'dominant_emotion': 'happy'
    }

    # Guardar como JSON
    client.set('analysis:123', json.dumps(analysis_result))

    # Recuperar y parsear
    data_raw = client.get('analysis:123')
    data = json.loads(data_raw)
    print(f"✅ Análisis guardado: {data['id']}")
    print(f"   Emoción dominante: {data['dominant_emotion']}")
    print(f"   Puntuaciones: {data['emotions']}")

    print()

def rate_limiting_example():
    """Implementar rate limiting simple"""
    print("4. RATE LIMITING")
    print("-" * 40)

    user_id = 42
    max_requests = 5
    window_seconds = 10

    key = f"rate_limit:user:{user_id}"

    # Simular requests
    print(f"Límite: {max_requests} requests en {window_seconds} segundos")

    for i in range(7):
        # Verificar contador actual
        current = client.get(key)

        if current is None:
            # Primera request
            client.setex(key, window_seconds, 1)
            print(f"  Request {i+1}: ✅ Permitida (primera request)")
        elif int(current) < max_requests:
            # Incrementar contador
            client.incr(key)
            print(f"  Request {i+1}: ✅ Permitida ({int(current)+1}/{max_requests})")
        else:
            # Límite alcanzado
            ttl = client.ttl(key)
            print(f"  Request {i+1}: ❌ Bloqueada (límite alcanzado, espera {ttl}s)")

        time.sleep(0.5)

    print()

def caching_example():
    """Ejemplo de caché con Redis"""
    print("5. SISTEMA DE CACHÉ")
    print("-" * 40)

    def get_user_from_db(user_id):
        """Simular consulta costosa a base de datos"""
        time.sleep(1)  # Simular latencia
        return {
            'id': user_id,
            'name': f'Usuario {user_id}',
            'email': f'user{user_id}@example.com'
        }

    def get_user(user_id):
        """Obtener usuario con caché"""
        cache_key = f"cache:user:{user_id}"

        # Intentar obtener del caché
        cached = client.get(cache_key)
        if cached:
            print(f"  📦 HIT: Datos del caché")
            return json.loads(cached)

        # No está en caché, obtener de DB
        print(f"  💾 MISS: Consultando base de datos...")
        user = get_user_from_db(user_id)

        # Guardar en caché por 5 minutos
        client.setex(cache_key, 300, json.dumps(user))

        return user

    # Primera llamada (sin caché)
    start = time.time()
    user = get_user(1)
    print(f"  Tiempo: {time.time() - start:.2f}s")
    print(f"  Usuario: {user['name']}")

    # Segunda llamada (con caché)
    start = time.time()
    user = get_user(1)
    print(f"  Tiempo: {time.time() - start:.2f}s")
    print(f"  Usuario: {user['name']}")

    print()

def list_operations():
    """Operaciones con listas"""
    print("6. OPERACIONES CON LISTAS")
    print("-" * 40)

    queue_key = "tasks:pending"

    # Limpiar lista anterior
    client.delete(queue_key)

    # Agregar tareas a la cola
    client.rpush(queue_key, "tarea1", "tarea2", "tarea3")
    print(f"✅ Agregadas 3 tareas a la cola")

    # Ver longitud de la lista
    length = client.llen(queue_key)
    print(f"📊 Tareas pendientes: {length}")

    # Obtener y remover primera tarea (FIFO)
    task = client.lpop(queue_key)
    print(f"✅ Procesando: {task}")

    # Ver todas las tareas restantes
    remaining = client.lrange(queue_key, 0, -1)
    print(f"📋 Tareas restantes: {remaining}")

    print()

def hash_operations():
    """Operaciones con hashes (como objetos)"""
    print("7. OPERACIONES CON HASHES")
    print("-" * 40)

    # Guardar datos de usuario como hash
    user_key = "user:profile:100"

    client.hset(user_key, mapping={
        'nombre': 'María García',
        'email': 'maria@example.com',
        'rol': 'cuidador',
        'ultimo_acceso': datetime.now().isoformat()
    })

    # Obtener campo específico
    nombre = client.hget(user_key, 'nombre')
    print(f"✅ Nombre: {nombre}")

    # Obtener todos los campos
    profile = client.hgetall(user_key)
    print(f"✅ Perfil completo:")
    for key, value in profile.items():
        print(f"   {key}: {value}")

    # Actualizar campo
    client.hset(user_key, 'ultimo_acceso', datetime.now().isoformat())
    print(f"✅ Último acceso actualizado")

    print()

def session_management():
    """Gestión de sesiones de usuario"""
    print("8. GESTIÓN DE SESIONES")
    print("-" * 40)

    session_id = "sess_abc123xyz"
    user_data = {
        'user_id': 1,
        'username': 'jhafet',
        'roles': ['cuidador', 'admin'],
        'login_time': datetime.now().isoformat()
    }

    # Crear sesión con expiración de 30 minutos
    session_key = f"session:{session_id}"
    client.setex(session_key, 1800, json.dumps(user_data))

    print(f"✅ Sesión creada: {session_id}")
    print(f"   Usuario: {user_data['username']}")
    print(f"   Roles: {user_data['roles']}")

    # Extender sesión (renovar TTL)
    client.expire(session_key, 1800)
    ttl = client.ttl(session_key)
    print(f"✅ Sesión renovada, expira en: {ttl} segundos")

    # Cerrar sesión (eliminar)
    # client.delete(session_key)
    # print("✅ Sesión cerrada")

    print()

def cleanup():
    """Limpiar datos de prueba"""
    print("9. LIMPIEZA")
    print("-" * 40)

    # Obtener todas las claves creadas en este test
    test_keys = [
        'usuario:1:nombre',
        'token:abc123',
        'visitas',
        'analysis:123',
        'rate_limit:user:42',
        'cache:user:1',
        'tasks:pending',
        'user:profile:100',
        'session:sess_abc123xyz'
    ]

    for key in test_keys:
        if client.exists(key):
            client.delete(key)
            print(f"🗑️  Eliminada: {key}")

    print("\n✅ Limpieza completada")

if __name__ == "__main__":
    print("=" * 50)
    print("PRUEBAS DE REDIS PARA EMOTION-ML-SERVICE")
    print("=" * 50)
    print()

    # Probar conexión primero
    if not test_connection():
        exit(1)

    # Ejecutar ejemplos
    basic_operations()
    work_with_json()
    rate_limiting_example()
    caching_example()
    list_operations()
    hash_operations()
    session_management()

    # Limpiar al final
    cleanup()

    print("\n" + "=" * 50)
    print("✅ TODAS LAS PRUEBAS COMPLETADAS")
    print("=" * 50)