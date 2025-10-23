# Microservicio de Usuarios - JWT + Firestore + MFA

## 🔍 Descripción
- Gestiona registro, login y administración de usuarios para el ecosistema Étikos.
- Autenticación local con JWT, contraseñas encriptadas con BCrypt y roles `CUSTOMER` / `ADMIN`.
- Persistencia en Firebase Firestore con auditoría delegada a un microservicio externo.
- Soporte de MFA: Google Authenticator (TOTP) y preferencia de login biométrico para clientes móviles.

## ✅ Requisitos previos
- Java 21 (JDK) y Maven 3.9+ o el wrapper `./mvnw`.
- Proyecto de Firebase con Firestore habilitado y credenciales de servicio.
- Acceso al microservicio de auditoría (por defecto `http://localhost:8003`).
- Opcional: herramientas CLI (`curl`, Postman) para probar los endpoints.

## ⚙️ Configuración inicial

### 1. Credenciales de Firebase
Coloca el archivo `firebase-service-account.json` dentro de `user/src/main/resources/`. Este archivo es leído automáticamente por `FirebaseConfig`.

### 2. Variables de entorno (`.env`)
El starter `spring-dotenv` carga automáticamente el archivo `.env` que se encuentre en `src/main/resources/`.

| Variable | Ejemplo | Descripción |
|----------|---------|-------------|
| `FIREBASE_PROJECT_ID` | `etikos-33906` | ID del proyecto Firebase (se usa para logs y métricas). |
| `JWT_SECRET` | `JorWkreMBGabS7odK7NbGidb6mFanOBbrisQBRAohP4=` | Clave Base64 de al menos 256 bits para firmar JWT. |
| `AUDIT_SERVICE_BASE_URL` | `http://localhost:8003` | URL base del microservicio de auditoría secundaria. |

> Genera el secreto JWT con al menos 32 bytes aleatorios. Evita compartirlo o versionarlo.

### 3. `application.properties`
```properties
server.port=8002
spring.application.name=user

firebase.project-id=${FIREBASE_PROJECT_ID}
firebase.credentials=classpath:firebase-service-account.json

jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration

audit.service.base-url=${AUDIT_SERVICE_BASE_URL}
```
**Generar un secret seguro:** jwt.secret=TU_SECRET_SEGURO_MINIMO_256_BITS
```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

### 4. Orígenes CORS permitidos
Modifica la lista en `user/src/main/java/com/etikos/user/config/CorsConfig.java` para reflejar los dominios front-end autorizados (Angular/Ionic, apps móviles, etc.).

### 5. Integración con auditoría
`AuditService` envía cada evento a `${AUDIT_SERVICE_BASE_URL}/api/audits`. Si el servicio no responde, se registran logs locales y se lanza una excepción controlada.

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

El servicio queda disponible en `http://localhost:8002`.

## Documentacion OpenAPI y Swagger UI
- Con la aplicacion en marcha abre `http://localhost:8002/swagger-ui/index.html` para explorar la API de forma interactiva.
- El JSON de OpenAPI queda expuesto en `http://localhost:8002/v3/api-docs`; importa la URL en Postman, Insomnia u otra herramienta.
- Mantiene sincronizado el archivo `openapi.yaml` en la raiz del repositorio cuando anadas o modifiques endpoints.

## Dockerizacion
- Desde la carpeta `microservices` ejecuta `docker compose build user-service` para generar la imagen con el `Dockerfile` incluido.
- Define `FIREBASE_PROJECT_ID`, `JWT_SECRET` y `AUDIT_SERVICE_BASE_URL` en `.env`; Docker Compose los inyecta como variables de entorno.
- Monta `firebase-service-account.json` si lo mantienes fuera del classpath (ver volumen configurado en `docker-compose.yml`).
- Levanta el contenedor con `docker compose up user-service` (o todo el stack con `docker compose up`) y accede via `http://localhost:8002`.
- Los logs del contenedor pueden mapearse a `./logs`; ajusta el volumen si prefieres otra ruta local.

## 🗃️ Modelo de datos (Firestore)

### Colección `users`
```json
{
  "uid": "string (ID autogenerado)",
  "username": "string (único)",
  "email": "string (único)",
  "password": "string (hash BCrypt)",
  "name": "string",
  "lastname": "string",
  "role": "CUSTOMER | ADMIN",
  "disabled": false,
  "biometricEnabled": false,
  "totpEnabled": false,
  "totpSecret": null,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```
`totpSecret` solo existe mientras el usuario tiene MFA habilitado; se borra al desactivar TOTP.

### Colección `audit_logs`
```json
{
  "uid": "string (usuario afectado)",
  "actorUid": "string (quien ejecutó la acción)",
  "action": "REGISTER | LOGIN | LOGIN_FAILED | LOGOUT | ...",
  "timestamp": "timestamp",
  "ip": "string",
  "userAgent": "string",
  "meta": {
    "...": "Datos contextuales (motivos de bloqueo, método de login, etc.)"
  }
}
```

## 🏗️ Arquitectura del Sistema
```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ HTTP Request
       │ Authorization: Bearer JWT
       ↓
┌─────────────────────┐
│  Spring Security    │
│  JwtTokenFilter     │ ← Valida JWT en cada request
└─────────┬───────────┘
          │
     ┌────┴────┐
     ↓         ↓
┌──────────┐ ┌──────────┐
│  User    │ │  Audit   │
│ Service  │ │ Service  │
└────┬─────┘ └────┬─────┘
     │            │
     └─────┬──────┘
           ↓
    ┌─────────────┐
    │  Firestore  │
    │   Client    │
    └──────┬──────┘
           │
           ↓
    ┌─────────────┐
    │  Firebase   │
    │  Firestore  │
    └─────────────┘
```

## 🔐 Flujos de autenticación y MFA

### 1. Registro y login estándar
```http
POST /users/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123",
  "name": "John",
  "lastname": "Doe"
}
```

```http
POST /users/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123"
}
```

Respuesta con credenciales válidas (sin TOTP habilitado):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "totpRequired": false,
  "tempSessionId": null,
  "user": {
    "uid": "firebase-generated-id",
    "username": "johndoe",
    "email": "john@example.com",
    "name": "John",
    "lastname": "Doe",
    "role": "CUSTOMER",
    "disabled": false,
    "biometricEnabled": false,
    "totpEnabled": false
  }
}
```

### 2. MFA con Google Authenticator (TOTP)

1. **Inicializar**  
   `POST /users/totp/setup` (requiere JWT).  
   Respuesta:
   ```json
   {
     "secret": "KZ3B2PBYRJE2....",
     "qrCodeDataUri": "data:image/png;base64,iVBORw0..."
   }
   ```

2. **Verificar y habilitar**  
   `POST /users/totp/verify` con cuerpo `{"code": "123456"}`.

3. **Login**  
   Cuando TOTP está activo, `/users/login` responde:
   ```json
   {
     "token": null,
     "totpRequired": true,
     "tempSessionId": "firebase-uid",
     "user": { ... }
   }
   ```
   Luego completa el proceso con  
   `POST /users/login/totp?uid=firebase-uid` y cuerpo `{"code": "123456"}` para recibir el JWT definitivo.

4. **Deshabilitar**  
   `POST /users/totp/disable` (JWT) con el código actual; limpia `totpSecret` y marca `totpEnabled=false`.

### 3. Login biométrico

1. El usuario habilita la preferencia en cualquier momento:  
   `PUT /users/biometric` con cuerpo `{"enabled": true}` (requiere JWT).

2. El cliente móvil realiza `POST /users/login/biometric?uid={uid}`.  
   - Si la cuenta tiene TOTP activo, la respuesta incluirá `totpRequired=true` y `tempSessionId` para completar con `/users/login/totp`.  
   - Si no hay TOTP, se devuelve el JWT inmediatamente.

3. Consultas adicionales:  
   - `GET /users/{uid}/biometric` (propietario o admin) devuelve `{"enabled": true}`.  
   - `GET /users/biometric-status` (ADMIN) lista el estado de todos los usuarios.

## 🌐 API REST

### Endpoints públicos
| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/users/register` | Registrar nuevo usuario. |
| `POST` | `/users/login` | Login con email y password. |
| `POST` | `/users/login/biometric` | Login usando UID y validación biométrica previa. |
| `POST` | `/users/login/totp` | Segunda fase del login cuando `totpRequired=true`. |
| `POST` | `/users/password-reset` | Solicitar envío (placeholder) de link de reset. |
| `POST` | `/users/audit/logout` | Registrar manualmente un logout. |
| `POST` | `/users/audit/login-failed` | Registrar manualmente un login fallido (útil en clientes externos). |

### Endpoints autenticados (JWT requerido)
| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/users/totp/status` | Verifica si el usuario autenticado tiene TOTP activo. |
| `POST` | `/users/totp/setup` | Genera secreto y QR para iniciar MFA. |
| `POST` | `/users/totp/verify` | Valida el código y habilita TOTP. |
| `POST` | `/users/totp/disable` | Deshabilita TOTP con un código válido. |
| `PUT` | `/users/biometric` | Activa o desactiva la preferencia biométrica propia. |
| `GET` | `/users/{uid}/biometric` | Consulta preferencia biométrica (propio o ADMIN). |

### Endpoints ADMIN (`ROLE_ADMIN`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/users` | Lista todos los usuarios. |
| `GET` | `/users/{uid}` | Obtiene el perfil por UID. |
| `PUT` | `/users/{uid}/credentials` | Cambia email y/o password (re-hasheado). |
| `PUT` | `/users/{uid}/block` | Bloquea o desbloquea usuarios (`disabled`). |
| `DELETE` | `/users/{uid}` | Elimina al usuario de Firestore. |
| `GET` | `/users/biometric-status` | Lista el estado biométrico de todos los usuarios. |

Todas las llamadas protegidas deben incluir `Authorization: Bearer <token>`.

## 📡 Auditoría

Cada operación relevante se envía al servicio secundario con un `AuditLogPayload`. Acciones disponibles:

| Acción | Cuándo se dispara |
|--------|-------------------|
| `REGISTER` | Registro de usuario. |
| `LOGIN` | Login exitoso (password, biométrico o TOTP). |
| `LOGOUT` | Logout registrado vía `/users/audit/logout`. |
| `LOGIN_FAILED` | Errores de autenticación (password, biométrico, TOTP). |
| `USER_BLOCKED` / `USER_UNBLOCKED` | Bloqueo/desbloqueo por un ADMIN. |
| `CREDENTIALS_UPDATED` | Cambios de email/password, activación/desactivación de TOTP o biometría. |
| `PASSWORD_RESET_LINK_SENT` | Solicitud de reset (placeholder). |

Si el servicio de auditoría no está disponible, la operación principal continúa y se loggea el error.

## 🔒 Seguridad

- JWT firmado con HMAC-SHA256 (`jwt.secret`), expiración por defecto 24 h.
- Contraseñas almacenadas con BCrypt (`BCryptPasswordEncoder`).
- MFA opcional con TOTP; el secreto se elimina al desactivar.
- Roles gestionados por Spring Security + `@PreAuthorize`.
- CORS configurado explícitamente para los clientes permitidos.
- Integración con auditoría para trazabilidad de acciones sensibles.

## 🐛 Solución de problemas

- **Firebase initialization failed**: verifica que `firebase-service-account.json` exista y tenga permisos.
- **Invalid JWT token**: revisa expiración, formato `Bearer <token>` y que front/back compartan el mismo `JWT_SECRET`.
- **Access Denied / 403**: comprueba rol `ADMIN` para endpoints protegidos y que el JWT no esté expirado.
- **Email already exists**: Firestore valida unicidad mediante queries previas al registro.
- **TOTP not set up**: llama a `/users/totp/setup` antes de verificar; asegúrate de ingresar el código dentro de 30 seg.
- **Audit service unreachable**: confirma `AUDIT_SERVICE_BASE_URL` y que el microservicio secundario esté activo.

## 📚 Estructura del proyecto

```
src/main/java/com/etikos/user/
├── UserApplication.java
├── audit/
│   ├── AuditAction.java
│   ├── AuditLogPayload.java
│   ├── AuditService.java
│   └── AuditServiceProperties.java
├── config/
│   ├── CorsConfig.java
│   └── FirebaseConfig.java
├── controller/
│   └── UserController.java
├── dto/
│   ├── BlockRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   ├── RegisterRequest.java
│   ├── TotpSetupResponse.java
│   ├── TotpVerifyRequest.java
│   ├── TotpLoginRequest.java
│   ├── UpdateCredentialsRequest.java
│   └── UserProfileDto.java
├── security/
│   ├── JwtService.java
│   ├── JwtTokenFilter.java
│   ├── MethodSecurityConfig.java
│   └── SecurityConfig.java
└── services/
    ├── TotpService.java
    └── UserProfileService.java
```

## ✅ Checklist para entornos productivos

- Cambia `JWT_SECRET` por uno único y gestionado en un vault.
- Habilita HTTPS en el gateway o proxy frontal.
- Refuerza reglas de Firestore para acceso solo vía Admin SDK.
- Ajusta `AUDIT_SERVICE_BASE_URL` al dominio real y monitorea su disponibilidad.
- Habilita MFA obligatorio para cuentas ADMIN si tu modelo de riesgo lo requiere.

¡Listo! El microservicio de usuarios queda documentado con la configuración actual.
