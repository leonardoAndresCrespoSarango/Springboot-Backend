# Arquitectura de contenedores

Este repositorio agrupa dos microservicios Java orquestados via Docker Compose: `user-service` (autenticacion sobre Firebase/Firestore) y `second-stack-service` (auditoria sobre PostgreSQL). Ambos se empaquetan como imagenes independientes y se conectan mediante la red bridge `etikos-backend-network`.

## Componentes
- **user-service (`./user`)**: API de usuarios con JWT, MFA y cliente contra el servicio de auditoria. Expone el puerto `8002` y depende de credenciales de Firebase.
- **second-stack-service (`./second_stack`)**: API de auditoria que persiste eventos en PostgreSQL o Supabase. Expone el puerto `8003`.
- **Volumen de logs**: `./logs` se monta opcionalmente para revisar los logs generados dentro de los contenedores.
- **Health checks**: Compose valida `/actuator/health` en ambos servicios antes de declararlos saludables.

Consulta la documentacion especifica de cada microservicio en:
- `user/README.md`
- `second_stack/README.md`
- `openapi.yaml` para la especificacion OpenAPI consolidada.

## Requisitos previos
- Docker Engine y el plugin Docker Compose (v2) instalados.
- Java y Maven solo si quieres construir o ejecutar los servicios fuera de contenedores.
- Archivo `.env` en la raiz con las variables necesarias (revisa `.env.example`).

Variables clave utilizadas por `docker-compose.yml`:

```
FIREBASE_PROJECT_ID=...
JWT_SECRET=...
AUDIT_SERVICE_BASE_URL=http://second-stack-service:8003
SUPABASE_JDBC_URL=jdbc:postgresql://...
```

Las credenciales de Firebase se esperan en `./user/src/main/resources/firebase-service-account.json`. Puedes montarlas como volumen si prefieres mantenerlas fuera del classpath.

## Flujo de despliegue con Docker Compose
1. **Construir imagenes**
   ```bash
   docker compose build
   ```
2. **Levantar servicios (modo adjunto)**
   ```bash
   docker compose up
   ```
3. **Levantar servicios en segundo plano**
   ```bash
   docker compose up -d
   ```
4. **Detener y liberar recursos**
   ```bash
   docker compose down
   ```

Una vez en marcha:
- Swagger UI user-service: `http://localhost:8002/swagger-ui/index.html`
- Swagger UI second-stack-service: `http://localhost:8003/swagger-ui/index.html`
- OpenAPI JSON consolidado: `http://localhost:8002/v3/api-docs` y `http://localhost:8003/v3/api-docs`
- Health checks: `http://localhost:8002/actuator/health`, `http://localhost:8003/actuator/health`

## Operaciones comunes
- **Revisar estado**: `docker compose ps`
- **Ver logs de todos los contenedores**: `docker compose logs -f`
- **Ver logs de un servicio**: `docker compose logs -f user-service`
- **Entrar a un contenedor**: `docker compose exec user-service sh`
- **Recrear con build forzado**: `docker compose up -d --build`
- **Limpiar todo (contenedores, red, volumenes)**: `docker compose down -v`

## Consideraciones de red y dependencias
- Dentro de la red interna usa los hostnames `user-service` y `second-stack-service` para comunicar servicios.
- Asegura que la URL JDBC de Supabase/Postgres sea alcanzable desde los contenedores (VPN, whitelist de IPs, etc.).
- Puedes ajustar recursos JVM modificando `JAVA_OPTS` en `docker-compose.yml`.

## Buenas practicas
- No versionar `.env` con secretos reales; usa gestores como Docker secrets, Vault o almacenes cloud en entornos productivos.
- Automatiza la regeneracion de imagenes con pipelines que ejecuten `docker buildx` si necesitas multiplataforma.
- Monitorea consumo con `docker stats` y mantente atento a los health checks para detectar reinicios inesperados.

Para detalles adicionales de comandos y troubleshooting consulta `DOCKER_GUIDE.md`.
