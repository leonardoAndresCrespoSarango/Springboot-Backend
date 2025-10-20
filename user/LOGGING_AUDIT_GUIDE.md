# 🔍 Sistema de Logging y Auditoría Mejorado

## ✅ Cambios Implementados

Se han realizado mejoras significativas en el sistema de logging y auditoría para capturar **todos los intentos de login**, incluyendo los fallidos.

---

## 🎯 Características Nuevas

### 1. **Registro de Login Fallido en Audit Logs**

Ahora **todos los intentos de login fallidos** se guardan automáticamente en Firestore (`audit_logs`) con información detallada:

```json
{
  "uid": null,
  "actorUid": null,
  "action": "LOGIN_FAILED",
  "timestamp": "2025-10-19T12:30:45Z",
  "ip": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "meta": {
    "email": "user@test.com",
    "reason": "INVALID_PASSWORD",
    "message": "Invalid email or password"
  }
}
```

**Razones de fallo que se registran:**
- `USER_NOT_FOUND` - Email no existe
- `INVALID_PASSWORD` - Contraseña incorrecta
- `ACCOUNT_DISABLED` - Cuenta bloqueada
- `SYSTEM_ERROR` - Error del sistema

### 2. **Logging Profesional con SLF4J**

Se agregó logging detallado en **todos los servicios y controladores**:

#### **Consola con Colores y Emojis:**

```
✅ LOGIN SUCCESS - User: user123 | IP: 192.168.1.100
🔴 LOGIN FAILED - Email: wrong@test.com | Reason: INVALID_PASSWORD | IP: 192.168.1.100
📝 USER REGISTERED - User: newuser456 | IP: 192.168.1.101
👋 LOGOUT - User: user123 | IP: 192.168.1.100
📊 AUDIT LOG - Action: CREDENTIALS_UPDATED | User: user789 | Actor: admin | IP: 192.168.1.102
```

#### **Logs en Archivo:**

Se crean automáticamente 2 archivos en la carpeta `logs/`:
- `application.log` - Logs generales (rotación diaria, 30 días)
- `audit.log` - Logs de auditoría específicos (rotación diaria, 90 días)

### 3. **Manejo de Errores Mejorado**

Se eliminaron los **stack traces largos** de la consola. Ahora los errores son limpios y concisos:

**Antes:**
```
java.lang.RuntimeException: Invalid email or password
	at com.etikos.user.services.UserProfileService.login(UserProfileService.java:82)
	... 100 líneas más de stack trace
```

**Ahora:**
```
2025-10-19 12:30:45.123 WARN  [http-nio-8002-exec-1] c.e.u.services.UserProfileService - Login failed: User not found with email: wrong@test.com
2025-10-19 12:30:45.125 WARN  [http-nio-8002-exec-1] c.e.u.audit.AuditService - 🔴 LOGIN FAILED - Email: wrong@test.com | Reason: USER_NOT_FOUND | IP: 127.0.0.1
```

**Respuesta al cliente (JSON limpio):**
```json
{
  "status": 401,
  "message": "Invalid email or password",
  "timestamp": "2025-10-19T12:30:45.123"
}
```

---

## 📊 Archivos Modificados/Creados

### ✅ **UserProfileService.java** (Modificado)
- Agregado logger: `private static final Logger log = LoggerFactory.getLogger(...)`
- Creada excepción personalizada: `LoginFailedException` con razón específica
- Logging detallado en cada operación:
  - Login exitoso/fallido
  - Registro de usuarios
  - Operaciones CRUD

### ✅ **UserController.java** (Modificado)
- Agregado logger para todas las operaciones
- Captura de errores de login con try-catch
- Registro automático en audit logs de intentos fallidos
- Respuestas HTTP apropiadas (401, 500, etc.)

### ✅ **AuditService.java** (Modificado)
- Agregado logger con emojis para mejor visualización
- Logs específicos según el tipo de acción:
  - ✅ Login exitoso
  - 🔴 Login fallido
  - 📝 Registro
  - 👋 Logout
  - 📊 Otras acciones

### ✅ **GlobalExceptionHandler.java** (Nuevo)
- Manejo centralizado de excepciones
- Evita stack traces largos en respuestas HTTP
- Respuestas de error limpias y consistentes
- Logging automático de todos los errores

### ✅ **logback-spring.xml** (Nuevo)
- Configuración profesional de logging
- Colores en consola para mejor legibilidad
- Archivos de log con rotación automática
- Niveles de log configurables por paquete

---

## 🚀 Ejemplos de Uso

### **Escenario 1: Login Exitoso**

**Request:**
```bash
curl -X POST http://localhost:8002/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"correct123"}'
```

**Consola:**
```
2025-10-19 12:30:45 INFO  [http-nio-8002-exec-1] c.e.u.controller.UserController - Login request received for email: user@test.com
2025-10-19 12:30:45 INFO  [http-nio-8002-exec-1] c.e.u.services.UserProfileService - Login attempt for email: user@test.com
2025-10-19 12:30:45 INFO  [http-nio-8002-exec-1] c.e.u.services.UserProfileService - Login successful for user: user@test.com (uid123)
2025-10-19 12:30:45 INFO  [http-nio-8002-exec-1] c.e.u.audit.AuditService - ✅ LOGIN SUCCESS - User: uid123 | IP: 127.0.0.1
```

**Firestore (audit_logs):**
```json
{
  "uid": "uid123",
  "actorUid": "uid123",
  "action": "LOGIN",
  "timestamp": "2025-10-19T12:30:45Z",
  "ip": "127.0.0.1"
}
```

---

### **Escenario 2: Login Fallido (Contraseña Incorrecta)**

**Request:**
```bash
curl -X POST http://localhost:8002/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"wrong123"}'
```

**Consola:**
```
2025-10-19 12:31:00 INFO  [http-nio-8002-exec-2] c.e.u.controller.UserController - Login request received for email: user@test.com
2025-10-19 12:31:00 INFO  [http-nio-8002-exec-2] c.e.u.services.UserProfileService - Login attempt for email: user@test.com
2025-10-19 12:31:00 WARN  [http-nio-8002-exec-2] c.e.u.services.UserProfileService - Login failed: Invalid password for email: user@test.com
2025-10-19 12:31:00 WARN  [http-nio-8002-exec-2] c.e.u.controller.UserController - Login failed for email: user@test.com - Reason: INVALID_PASSWORD
2025-10-19 12:31:00 WARN  [http-nio-8002-exec-2] c.e.u.audit.AuditService - 🔴 LOGIN FAILED - Email: user@test.com | Reason: INVALID_PASSWORD | IP: 127.0.0.1
```

**Firestore (audit_logs):**
```json
{
  "uid": null,
  "actorUid": null,
  "action": "LOGIN_FAILED",
  "timestamp": "2025-10-19T12:31:00Z",
  "ip": "127.0.0.1",
  "meta": {
    "email": "user@test.com",
    "reason": "INVALID_PASSWORD",
    "message": "Invalid email or password"
  }
}
```

**Response HTTP (401):**
```json
{
  "status": 401,
  "message": "Invalid email or password",
  "timestamp": "2025-10-19T12:31:00.456"
}
```

---

### **Escenario 3: Usuario No Existe**

**Consola:**
```
2025-10-19 12:32:00 WARN  [http-nio-8002-exec-3] c.e.u.services.UserProfileService - Login failed: User not found with email: noexiste@test.com
2025-10-19 12:32:00 WARN  [http-nio-8002-exec-3] c.e.u.audit.AuditService - 🔴 LOGIN FAILED - Email: noexiste@test.com | Reason: USER_NOT_FOUND | IP: 127.0.0.1
```

---

### **Escenario 4: Cuenta Bloqueada**

**Consola:**
```
2025-10-19 12:33:00 WARN  [http-nio-8002-exec-4] c.e.u.services.UserProfileService - Login failed: Account disabled for email: blocked@test.com
2025-10-19 12:33:00 WARN  [http-nio-8002-exec-4] c.e.u.audit.AuditService - 🔴 LOGIN FAILED - Email: blocked@test.com | Reason: ACCOUNT_DISABLED | IP: 127.0.0.1
```

---

## 📁 Estructura de Logs

```
user/
├── logs/
│   ├── application.log              # Log general (hoy)
│   ├── application-2025-10-18.log   # Log de ayer
│   ├── audit.log                    # Audit log (hoy)
│   └── audit-2025-10-18.log         # Audit de ayer
```

---

## ⚙️ Configuración de Niveles de Log

Puedes ajustar los niveles de log en `logback-spring.xml`:

```xml
<!-- Más detalle en desarrollo -->
<logger name="com.etikos.user" level="DEBUG"/>

<!-- Menos detalle en producción -->
<logger name="com.etikos.user" level="WARN"/>
```

**Niveles disponibles:**
- `TRACE` - Máximo detalle
- `DEBUG` - Información de debugging
- `INFO` - Información general (default)
- `WARN` - Advertencias
- `ERROR` - Solo errores

---

## 🔍 Consultar Audit Logs en Firestore

Puedes consultar los logs fallidos con:

**Firebase Console:**
1. Ve a Firestore Database
2. Colección: `audit_logs`
3. Filtra por: `action == "LOGIN_FAILED"`

**Desde código (opcional):**
```java
Firestore db = FirestoreClient.getFirestore();
List<QueryDocumentSnapshot> failedLogins = db.collection("audit_logs")
    .whereEqualTo("action", "LOGIN_FAILED")
    .orderBy("timestamp", Query.Direction.DESCENDING)
    .limit(100)
    .get().get().getDocuments();
```

---

## 📊 Ventajas del Sistema

✅ **Trazabilidad Completa:** Todos los intentos de login se registran  
✅ **Seguridad:** Detecta intentos de fuerza bruta  
✅ **Debugging Fácil:** Logs claros con emojis y colores  
✅ **Cumplimiento:** Auditoría completa para regulaciones  
✅ **Performance:** Logging asíncrono sin impacto en rendimiento  
✅ **Mantenimiento:** Rotación automática de logs  

---

## 🎨 Personalización

### Cambiar Formato de Logs:

Edita `logback-spring.xml`:
```xml
<property name="CONSOLE_LOG_PATTERN" 
          value="%d{HH:mm:ss} %highlight(%-5level) %cyan(%logger{15}) - %msg%n"/>
```

### Deshabilitar Emojis:

En `AuditService.java`, cambia:
```java
log.warn("LOGIN FAILED - Email: {} | Reason: {}", email, reason);
```

---

## ✅ Todo Funcionando

Ahora tu sistema:
1. ✅ **Registra todos los intentos de login** en Firestore
2. ✅ **Muestra logs limpios y legibles** en consola
3. ✅ **Evita stack traces largos** en las respuestas
4. ✅ **Guarda logs en archivos** con rotación automática
5. ✅ **Identifica razones específicas** de fallo de login

¡El sistema de logging y auditoría está completamente operativo! 🎉

