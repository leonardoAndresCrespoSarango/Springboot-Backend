# Microservicio de Auditoría - Postgres + Spring Boot

## 🔍 Descripción
- Centraliza los eventos de auditoría generados por otros microservicios (por ejemplo `user`) y los persiste en PostgreSQL.
- Expone una API REST para registrar (`POST /api/audits`) y consultar (`GET /api/audits`) eventos con filtros por usuario, acción y rango de fechas.
- Permite adjuntar metadatos arbitrarios en formato JSON, manteniendo un historial trazable de acciones críticas.

## ✅ Requisitos previos
- Java 21 (JDK) y Maven 3.9+ o el wrapper `./mvnw`.
- Instancia accesible de PostgreSQL (Supabase es la referencia actual).
- Credenciales con permisos de lectura/escritura sobre la base de datos.
- Opcional: herramientas como `curl` o Postman para probar la API.

## ⚙️ Configuración inicial

### 1. Configurar la base de datos PostgreSQL
- Crea la base de datos y usuario que usará el servicio (en Supabase o tu proveedor preferido).
- Habilita acceso desde la red donde está desplegado el microservicio.
- Si tu proveedor expone una URL “pooler”, úsala; reduce latencia y gestiona conexiones concurrentes.

### 2. Variables de entorno (`.env`)
El starter `spring-dotenv` carga automáticamente el archivo `.env` en `src/main/resources/`.

| Variable | Ejemplo | Descripción |
|----------|---------|-------------|
| `SUPABASE_DB_HOST` | `db.qqwdmubdufhjbndwqhxa.supabase.co` | Host del clúster de Postgres. |
| `SUPABASE_DB_PORT` | `5432` | Puerto TCP del clúster. |
| `SUPABASE_DB_NAME` | `postgres` | Base de datos que almacenará los logs. |
| `SUPABASE_DB_USER` | `postgres` | Usuario con permisos de lectura/escritura. |
| `SUPABASE_DB_PASSWORD` | `***` | Contraseña del usuario configurado. |
| `SUPABASE_JDBC_URL` | `jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres?user=...&password=...` | URL JDBC completa que usa Spring. |

> Escapa caracteres especiales en la URL y evita versionar credenciales reales.

### 3. `application.properties`
```properties
server.port=8003
spring.application.name=second_stack

spring.datasource.url=${SUPABASE_JDBC_URL}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

- `ddl-auto=update` crea o ajusta la tabla `audit_logs` en desarrollo. En producción usa migraciones controladas (Flyway/Liquibase).
- El servicio escucha en `http://localhost:8003`, coincidiendo con lo configurado en el microservicio `user`.

### 4. Orígenes CORS permitidos
`AuditLogController` expone `@CrossOrigin(origins = "*")` para simplificar pruebas. Antes de desplegar a producción especifica los dominios front-end que consumirán la API.

### 5. Integración con otros microservicios
- Los consumidores deben enviar sus eventos a `POST /api/audits`.
- Reutiliza el mismo conjunto de acciones definido en `AuditAction` para mantener consistencia.
- Ajusta timeouts/reintentos en los clientes para manejar interrupciones temporales.

## ▶️ Ejecución local
```bash
# Mac / Linux
./mvnw clean package
./mvnw spring-boot:run
```

```powershell
# Windows
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

El microservicio queda disponible en `http://localhost:8003`.

## 🗃️ Modelo de datos (PostgreSQL)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL (PK) | Identificador autogenerado. |
| `uid` | VARCHAR(150) | Usuario afectado por la acción (obligatorio). |
| `actor_uid` | VARCHAR(150) | Usuario que ejecutó la acción (opcional). |
| `action` | VARCHAR(60) | Acción registrada (`AuditAction`). |
| `timestamp` | TIMESTAMP | Momento del evento (`Instant.now()` por defecto). |
| `ip` | VARCHAR(100) | Dirección IP de origen. |
| `user_agent` | VARCHAR(500) | User-Agent recibido. |
| `metadata` | TEXT | JSON con información adicional, convertido vía `AuditMetadataConverter`. |

## 🏗️ Arquitectura del Sistema
```
┌──────────────┐
│ Microservicios│
│   productores │
└──────┬────────┘
       │  HTTP POST /api/audits
       ↓
┌────────────────────┐
│ AuditLogController │
└─────────┬──────────┘
          │
┌─────────▼──────────┐
│   AuditLogService  │
└─────────┬──────────┘
          │
┌─────────▼──────────┐
│ AuditLogRepository │
└─────────┬──────────┘
          │
┌─────────▼──────────┐
│ PostgreSQL /       │
│ Supabase (audit_logs)
└────────────────────┘
```

## 🔁 Flujos de auditoría

1. **Registro de evento**  
   - Un microservicio envía la carga a `POST /api/audits` con `uid`, `action`, metadatos y contexto (IP, User-Agent).  
   - Si no se incluye `timestamp`, el servicio asigna `Instant.now()` antes de persistirlo.

2. **Consulta y monitoreo**  
   - Analistas o servicios de reporting consumen `GET /api/audits` aplicando filtros por usuario, acción o ventana temporal.  
   - El resultado paginado incluye metadatos completos para facilitar inspecciones manuales o dashboards.

3. **Extensión de metadatos**  
   - Cualquier clave adicional en `metadata` se serializa a JSON y se recupera intacta al consultar, permitiendo enriquecer los eventos sin cambios en el esquema.

## 🌐 API REST

### Endpoints públicos
| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/audits` | Registra un evento de auditoría. |
| `GET` | `/api/audits` | Consulta eventos con filtros y paginación. |

### Detalles clave
- `POST /api/audits` valida `uid` y `action` y retorna el evento persistido (201 Created).
- `GET /api/audits` acepta `uid`, `action`, `from`, `to`, `page`, `size`, `sort`; ordena por `timestamp` descendente por defecto.
- Las respuestas utilizan el DTO `AuditLogResponse`, garantizando inmutabilidad y JSON limpio.

## 📡 Auditoría

- El enum `AuditAction` define las acciones permitidas: `REGISTER`, `LOGIN`, `LOGOUT`, `LOGIN_FAILED`, `PASSWORD_RESET_LINK_SENT`, `CREDENTIALS_UPDATED`, `USER_BLOCKED`, `USER_UNBLOCKED`, `ROLE_CHANGED`.
- El converter `AuditMetadataConverter` asegura que `metadata` se almacene y recupere como JSON sin pérdida.
- La integridad del historial depende de que los productores usen `uid`/`actorUid` consistentes y registren contexto relevante (IP, User-Agent, etc.).

## 🔒 Seguridad

- Protege las credenciales de la base de datos usando un gestor de secretos.
- Configura TLS tanto para la conexión con Postgres como para exponer la API detrás de un gateway seguro.
- Restringe CORS en producción y considera agregar autenticación/Bearer tokens si terceros no confiables pudieran acceder.
- Monitorea el tamaño de la tabla `audit_logs` y define políticas de retención o archivado para evitar crecimiento indefinido.

## 🐛 Solución de problemas

- **`PSQLException: connection refused`**: verifica `SUPABASE_JDBC_URL`, reglas de firewall y disponibilidad del clúster.
- **`relation "audit_logs" does not exist`**: ejecuta una vez con `ddl-auto=update` o aplica migraciones para crear la tabla.
- **`Metadata serialization error`**: revisa que el JSON enviado sea serializable y no contenga valores circulares.
- **`400 Bad Request` al registrar**: asegúrate de incluir `uid` y `action` válidos del enum `AuditAction`.
- **Resultados vacíos en consultas**: confirma parámetros `from/to` y zona horaria; el backend usa `Instant` (UTC).

## 📚 Estructura del proyecto

```
second_stack/
├── src/main/java/com/etikos/demo/
│   ├── SecondStackApplication.java
│   ├── controller/AuditLogController.java
│   ├── service/AuditLogService.java
│   ├── repository/AuditLogRepository.java
│   ├── entity/
│   │   ├── AuditLog.java
│   │   ├── AuditAction.java
│   │   └── AuditMetadataConverter.java
│   └── dto/
│       ├── AuditLogRequest.java
│       └── AuditLogResponse.java
├── src/main/resources/
│   ├── application.properties
│   └── .env
└── pom.xml
```

## ✅ Checklist para entornos productivos

- Sustituye credenciales de ejemplo por secretos gestionados (Vault, AWS Secrets Manager, etc.).
- Desactiva `spring.jpa.show-sql` y cambia `ddl-auto` a `validate`; administra el esquema con migraciones.
- Configura CORS restringido y añade autenticación/autorización si la API sale de la red interna.
- Monitoriza conexiones y consumo de almacenamiento; automatiza limpieza/archivado según tus políticas.
- Implementa alertas (logging, APM) para detectar fallos al recibir eventos críticos.

Listo: el microservicio de auditoría comparte ahora la misma estructura documental que el microservicio `user`.
