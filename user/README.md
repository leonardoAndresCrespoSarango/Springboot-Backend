# Microservicio de Usuarios - JWT + Firebase Firestore

## 🎯 Arquitectura

Este microservicio utiliza:
- **JWT (JSON Web Tokens)** para autenticación local
- **Firebase Firestore** como base de datos NoSQL
- **BCrypt** para encriptación de contraseñas
- **Spring Security** para control de acceso

## 🚀 Configuración

### 1. Configurar Firebase

Asegúrate de que el archivo `firebase-service-account.json` esté en `src/main/resources/` con las credenciales de tu proyecto Firebase.

### 2. Configurar JWT Secret

Edita `application.properties` y cambia el secret JWT:

```properties
jwt.secret=TU_SECRET_SEGURO_MINIMO_256_BITS
jwt.expiration=86400000
```

**Generar un secret seguro:**
```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

### 3. Ejecutar el Proyecto

```bash
# Compilar
.\mvnw.cmd clean package

# Ejecutar
.\mvnw.cmd spring-boot:run
```

El servidor estará en: **http://localhost:8002**

## 📊 Estructura de Firestore

### Colección `users`
```json
{
  "uid": "string (ID autogenerado)",
  "username": "string (único)",
  "email": "string (único)",
  "password": "string (BCrypt hash)",
  "name": "string",
  "lastname": "string",
  "role": "CUSTOMER | ADMIN",
  "disabled": false,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Colección `audit_logs`
```json
{
  "uid": "string (usuario afectado)",
  "actorUid": "string (quien realizó la acción)",
  "action": "REGISTER | LOGIN | LOGOUT | etc",
  "timestamp": "timestamp",
  "ip": "string",
  "userAgent": "string",
  "meta": "object (datos adicionales)"
}
```

## 🔐 Crear Usuario Administrador

Después del primer inicio, crea un usuario ADMIN manualmente en Firestore:

**En Firebase Console → Firestore Database → users → Add document:**

```json
{
  "uid": "admin-uid-001",
  "username": "admin",
  "email": "admin@etikos.com",
  "password": "$2a$10$XptfskLsT.yXcXHjkN7jnO7VHjJWX9OZq7g3Fh6C3NHn9bx8vMYYm",
  "name": "Admin",
  "lastname": "User",
  "role": "ADMIN",
  "disabled": false,
  "createdAt": "2025-10-19T00:00:00Z",
  "updatedAt": "2025-10-19T00:00:00Z"
}
```

> **Nota:** La contraseña hasheada corresponde a `admin123` - cámbiala después del primer login.

## 📡 API Endpoints

### 🟢 Endpoints Públicos

#### 1. Registrar Usuario
```http
POST http://localhost:8002/users/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123",
  "name": "John",
  "lastname": "Doe"
}
```

**Respuesta 200:**
```json
{
  "uid": "firebase-generated-id",
  "username": "johndoe",
  "email": "john@example.com",
  "name": "John",
  "lastname": "Doe",
  "role": "CUSTOMER"
}
```

#### 2. Login
```http
POST http://localhost:8002/users/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123"
}
```

**Respuesta 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "uid": "firebase-generated-id",
    "username": "johndoe",
    "email": "john@example.com",
    "name": "John",
    "lastname": "Doe",
    "role": "CUSTOMER"
  }
}
```

### 🔒 Endpoints Protegidos (requieren JWT)

**Header requerido en todas las peticiones:**
```
Authorization: Bearer {tu_token_jwt}
```

#### 3. Listar Todos los Usuarios 🔴 ADMIN
```http
GET http://localhost:8002/users
Authorization: Bearer {token}
```

#### 4. Obtener Usuario por UID 🔴 ADMIN
```http
GET http://localhost:8002/users/{uid}
Authorization: Bearer {token}
```

#### 5. Actualizar Credenciales 🔴 ADMIN
```http
PUT http://localhost:8002/users/{uid}/credentials
Authorization: Bearer {token}
Content-Type: application/json

{
  "newEmail": "newemail@example.com",
  "newPassword": "NewPassword123"
}
```

#### 6. Bloquear/Desbloquear Usuario 🔴 ADMIN
```http
PUT http://localhost:8002/users/{uid}/block
Authorization: Bearer {token}
Content-Type: application/json

{
  "disabled": true
}
```

#### 7. Eliminar Usuario 🔴 ADMIN
```http
DELETE http://localhost:8002/users/{uid}
Authorization: Bearer {token}
```

#### 8. Auditar Logout
```http
POST http://localhost:8002/users/audit/logout
Authorization: Bearer {token}
```

## 🧪 Pruebas con cURL

```bash
# 1. Registrar usuario
curl -X POST http://localhost:8002/users/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"test\",\"email\":\"test@test.com\",\"password\":\"Test123\",\"name\":\"Test\",\"lastname\":\"User\"}"

# 2. Login (guarda el token)
curl -X POST http://localhost:8002/users/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@test.com\",\"password\":\"Test123\"}"

# 3. Listar usuarios (reemplaza TOKEN)
curl -X GET http://localhost:8002/users ^
  -H "Authorization: Bearer TOKEN"

# 4. Obtener usuario específico
curl -X GET http://localhost:8002/users/{uid} ^
  -H "Authorization: Bearer TOKEN"
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

## 🛡️ Seguridad

### JWT Token
- **Algoritmo**: HMAC-SHA256
- **Contenido**: 
  - `username`: Nombre de usuario
  - `role`: Rol del usuario (CUSTOMER/ADMIN)
  - `userId`: UID de Firestore
- **Expiración**: 24 horas (configurable)
- **Formato**: `Authorization: Bearer {token}`

### Contraseñas
- **Hash**: BCrypt con salt automático
- **Rounds**: 10 (configuración por defecto de Spring Security)
- Nunca se almacenan en texto plano

### Control de Acceso
- Endpoints públicos: `/register`, `/login`, `/password-reset`
- Endpoints protegidos: Requieren JWT válido
- Endpoints ADMIN: Requieren `role=ADMIN` en el JWT
- Validación con `@PreAuthorize("hasRole('ADMIN')")`

## 📝 Auditoría

Todas las acciones se registran en `audit_logs`:

| Acción | Descripción |
|--------|-------------|
| `REGISTER` | Registro de nuevo usuario |
| `LOGIN` | Login exitoso |
| `LOGOUT` | Cierre de sesión |
| `LOGIN_FAILED` | Intento fallido de login |
| `CREDENTIALS_UPDATED` | Cambio de email/contraseña |
| `USER_BLOCKED` | Usuario bloqueado |
| `USER_UNBLOCKED` | Usuario desbloqueado |
| `PASSWORD_RESET_LINK_SENT` | Solicitud de reset |

## ⚙️ Tecnologías

- **Java 21**
- **Spring Boot 3.5.6**
- **Spring Security** (autenticación y autorización)
- **Firebase Admin SDK 9.3.0** (Firestore)
- **JJWT 0.11.5** (JWT)
- **BCrypt** (hash de contraseñas)

## ⚠️ Notas Importantes

1. **Producción**:
   - ⚠️ Cambia `jwt.secret` a un valor único y seguro
   - ⚠️ Usa HTTPS para todas las comunicaciones
   - ⚠️ Configura reglas de seguridad en Firestore
   - ⚠️ Habilita autenticación de dos factores para ADMIN

2. **Seguridad JWT**:
   - Los tokens no pueden ser revocados hasta que expiren
   - Si necesitas revocación, implementa una blacklist en Firestore
   - El secret JWT debe mantenerse privado y seguro

3. **Firebase Firestore**:
   - Los UIDs son generados automáticamente por Firestore
   - Índices automáticos en `email` y `username` para búsquedas
   - Las queries son case-sensitive

4. **Desarrollo**:
   - Los logs de consultas están deshabilitados por defecto
   - El servidor corre en puerto 8002
   - Los errores retornan códigos HTTP estándar

## 🐛 Solución de Problemas

### Error: "Firebase initialization failed"
- Verifica que `firebase-service-account.json` exista en `resources/`
- Confirma que las credenciales sean válidas
- Revisa los permisos del proyecto en Firebase Console

### Error: "Invalid JWT token"
- El token puede haber expirado (24 horas)
- Verifica el formato: `Bearer {token}` (con espacio)
- Asegúrate de usar el mismo secret para generar y validar

### Error: "Access Denied"
- Verifica que el usuario tenga el rol correcto en Firestore
- Los endpoints ADMIN requieren `role: "ADMIN"`
- El token debe ser válido y no estar expirado

### Error: "Email already exists"
- El email debe ser único en la colección `users`
- Verifica que no exista otro usuario con ese email

## 📚 Estructura del Proyecto

```
src/main/java/com/etikos/user/
├── UserApplication.java          # Clase principal
├── audit/
│   ├── AuditAction.java          # Enum de acciones auditables
│   └── AuditService.java         # Servicio de auditoría (Firestore)
├── config/
│   └── FirebaseConfig.java       # Configuración de Firebase
├── controller/
│   └── UserController.java       # REST API endpoints
├── dto/
│   ├── LoginRequest.java         # DTO para login
│   ├── LoginResponse.java        # DTO con token + user
│   ├── RegisterRequest.java      # DTO para registro
│   ├── UpdateCredentialsRequest.java
│   ├── BlockRequest.java
│   └── UserProfileDto.java       # DTO de usuario
├── security/
│   ├── JwtService.java           # Generación y validación JWT
│   ├── JwtTokenFilter.java       # Filtro de autenticación
│   ├── SecurityConfig.java       # Configuración Spring Security
│   └── MethodSecurityConfig.java # Habilitación de @PreAuthorize
└── services/
    └── UserProfileService.java  # Lógica de negocio (Firestore)
```

## 🎉 ¡Listo para Usar!

Tu microservicio ahora funciona con:
- ✅ JWT para autenticación local
- ✅ Firebase Firestore como base de datos
- ✅ BCrypt para contraseñas seguras
- ✅ Spring Security para control de acceso
- ✅ Auditoría completa de acciones

**No necesitas configurar PostgreSQL ni Supabase - todo funciona con Firebase!** 🔥

