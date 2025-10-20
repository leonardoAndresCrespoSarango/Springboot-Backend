# Configuración CORS - Backend

## ✅ CORS Configurado Exitosamente

He agregado la configuración CORS completa al proyecto para permitir que tu frontend en Angular pueda comunicarse con el backend sin problemas.

## 📁 Archivos Modificados/Creados

### 1. **CorsConfig.java** (NUEVO)
`src/main/java/com/etikos/user/config/CorsConfig.java`

Configuración centralizada de CORS que permite:

- ✅ **Orígenes permitidos:**
  - `http://localhost:4200` (Angular desarrollo)
  - `http://localhost:4201` (Puerto alternativo)
  - `http://localhost:3000` (Otro puerto común)
  - `https://tu-dominio.com` (Producción - cambiar por tu dominio real)

- ✅ **Métodos HTTP permitidos:**
  - GET, POST, PUT, DELETE, OPTIONS, PATCH

- ✅ **Headers permitidos:**
  - Todos los headers (`*`)
  - Incluye `Authorization` para JWT

- ✅ **Credenciales:**
  - `allowCredentials = true` (permite cookies y headers de autorización)

- ✅ **Cache preflight:**
  - 3600 segundos (1 hora) para peticiones OPTIONS

### 2. **SecurityConfig.java** (MODIFICADO)
`src/main/java/com/etikos/user/security/SecurityConfig.java`

Actualizado para integrar la configuración CORS:

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource))
```

Esto asegura que Spring Security aplique las reglas CORS antes de validar la seguridad.

---

## 🚀 Cómo Funciona

### Flujo de Petición CORS:

1. **Angular hace petición** desde `http://localhost:4200`
2. **Navegador envía preflight** (petición OPTIONS) al backend
3. **Backend responde** con headers CORS permitiendo el origen
4. **Navegador permite** la petición real (GET, POST, etc.)
5. **Backend procesa** la petición y responde
6. **Angular recibe** la respuesta exitosamente

### Headers CORS que el Backend Envía:

```
Access-Control-Allow-Origin: http://localhost:4200
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
Access-Control-Allow-Headers: *
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

---

## 🔧 Configuración para Producción

Antes de desplegar a producción, **DEBES actualizar** los orígenes permitidos en `CorsConfig.java`:

```java
configuration.setAllowedOrigins(Arrays.asList(
    "https://tu-dominio-frontend.com",     // Tu dominio de producción
    "https://www.tu-dominio-frontend.com"  // Con www si aplica
));
```

**⚠️ IMPORTANTE:** No usar `"*"` en producción con `allowCredentials = true`, es un riesgo de seguridad.

---

## 🧪 Probar CORS

### Desde Angular (Desarrollo):

```typescript
// En tu servicio Angular
constructor(private http: HttpClient) {}

// Esta petición ahora funcionará sin errores CORS
this.http.post('http://localhost:8002/users/login', {
  email: 'test@test.com',
  password: '123456'
}).subscribe({
  next: (response) => console.log('Funciona!', response),
  error: (error) => console.error('Error:', error)
});
```

### Verificar en el Navegador:

1. Abre las **DevTools** (F12)
2. Ve a la pestaña **Network**
3. Haz una petición desde Angular
4. Verás dos peticiones:
   - **OPTIONS** (preflight) - debe retornar 200 OK
   - **POST/GET** (real) - tu petición actual

Si ves errores CORS, revisa:
- Que el origen esté en la lista de `allowedOrigins`
- Que el backend esté corriendo en el puerto correcto (8002)
- Que Angular esté en un puerto permitido (4200)

---

## 📝 Configuraciones Adicionales (Opcional)

### Permitir Orígenes Dinámicos

Si necesitas permitir múltiples subdominios:

```java
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://*.tu-dominio.com",
    "https://*.tu-dominio.com"
));
```

### Restringir Headers Específicos

Si quieres mayor control sobre los headers:

```java
configuration.setAllowedHeaders(Arrays.asList(
    "Authorization",
    "Content-Type",
    "Accept",
    "X-Requested-With"
));
```

### Deshabilitar Credenciales (si no usas cookies)

```java
configuration.setAllowCredentials(false);
// Ahora puedes usar allowedOrigins("*")
```

---

## ✅ Verificación Final

El proyecto ya está configurado y listo para recibir peticiones desde Angular. La configuración CORS:

- ✅ Permite peticiones desde `localhost:4200` (Angular)
- ✅ Permite todos los métodos HTTP necesarios
- ✅ Permite el header `Authorization` para JWT
- ✅ Permite credenciales (para cookies y auth headers)
- ✅ Está integrado con Spring Security

**¡Tu backend ya puede comunicarse con Angular sin problemas de CORS!** 🎉

---

## 🐛 Troubleshooting

### Error: "CORS policy: No 'Access-Control-Allow-Origin' header"

**Solución:** Verifica que el origen de Angular esté en `allowedOrigins`

### Error: "CORS policy: credentials mode is 'include'"

**Solución:** Asegúrate que `allowCredentials = true` en el backend

### Error: Preflight OPTIONS retorna 403

**Solución:** Verifica que `/users/login` esté en `.permitAll()` en SecurityConfig

### Múltiples Headers CORS

**Solución:** Verifica que no tengas `@CrossOrigin` en los controladores (usa solo la configuración global)

