# Integración de Autenticación Biométrica (Huella Digital) en Ionic

Este documento describe los pasos para agregar autenticación biométrica (huella digital) como método alternativo de inicio de sesión en tu app Ionic y cómo gestionarla desde el backend.

## 1. Instalar el plugin de biometría

Para Ionic/Capacitor:

```bash
npm install @capacitor-fingerprint-auth
npx cap sync
```

Para Ionic/Cordova:

```bash
ionic cordova plugin add cordova-plugin-fingerprint-aio
npm install @ionic-native/fingerprint-aio
```

## 2. Configurar el plugin en tu app

### Ejemplo con Capacitor

```typescript
import { FingerprintAuth } from '@capacitor/fingerprint-auth';

async function authenticateWithFingerprint() {
  const result = await FingerprintAuth.authenticate({
    reason: 'Autenticación rápida con huella',
    title: 'Iniciar sesión',
    subtitle: 'Usa tu huella digital',
    description: 'Coloca tu dedo en el sensor',
  });
  if (result.verified) {
    // Recupera el JWT guardado y úsalo para acceder al backend
  }
}
```

### Ejemplo con Cordova

```typescript
import { FingerprintAIO } from '@ionic-native/fingerprint-aio/ngx';

constructor(private faio: FingerprintAIO) {}

loginWithFingerprint() {
  this.faio.show({
    title: 'Iniciar sesión',
    subtitle: 'Usa tu huella digital',
    description: 'Coloca tu dedo en el sensor',
    disableBackup: true,
  })
  .then((result: any) => {
    // Recupera el JWT guardado y úsalo para acceder al backend
  })
  .catch(error => {
    // Maneja errores o fallback a login tradicional
  });
}
```

## 3. Gestión de preferencia biométrica desde el frontend

### Activar o desactivar biometría

Cuando el usuario decida activar o desactivar la autenticación biométrica, realiza una petición al backend:

```typescript
// PUT /users/biometric
// Body: { "enabled": true } o { "enabled": false }

await httpClient.put('/users/biometric', { enabled: true });
```

### Consultar el estado de biometría

Para saber si el usuario tiene biometría activa:

```typescript
// GET /users/{uid}/biometric
const res = await httpClient.get(`/users/${uid}/biometric`);
const biometricEnabled = res.enabled;
```

### (Admin) Consultar el estado de todos los usuarios

```typescript
// GET /users/biometric-status
const res = await httpClient.get('/users/biometric-status');
// res es un array de objetos con uid, email, username, biometricEnabled
```

## 4. Flujo recomendado

1. El usuario inicia sesión tradicionalmente (usuario/contraseña) y se guarda el JWT localmente (por ejemplo, en `Storage`).
2. El usuario puede activar/desactivar la biometría desde la app (llamando al endpoint correspondiente).
3. En futuros inicios, si la biometría está activa, el usuario puede autenticarse con huella digital y desbloquear el JWT guardado.
4. Si falla la huella, se ofrece el login tradicional.

## 5. Consideraciones de seguridad
- El JWT debe guardarse de forma segura (por ejemplo, en `Secure Storage`).
- La autenticación biométrica solo desbloquea el acceso local, no valida contra el backend.
- El backend sigue validando el JWT en cada petición.
- El usuario y el admin pueden consultar el estado de biometría mediante los endpoints descritos.

## 6. Recursos
- [@capacitor-fingerprint-auth](https://github.com/martinrojas/CapacitorFingerprintAuth)
- [cordova-plugin-fingerprint-aio](https://github.com/ng-plus/ng-Plus-Fingerprint-AIO)

---

Este método permite un inicio de sesión rápido y seguro, manteniendo la validación robusta en el backend y permitiendo la gestión de la preferencia biométrica desde la app y el panel de administración.
