# Guía de Implementación TOTP (Google Authenticator)

## ✅ Implementación Completada en Backend

Se ha implementado exitosamente la autenticación TOTP (Time-based One-Time Password) en tu microservicio de usuarios de Spring Boot. Esta funcionalidad permite usar aplicaciones como Google Authenticator, Microsoft Authenticator, Authy, etc.

## 📋 Cambios Realizados

### 1. **Dependencias Agregadas** (pom.xml)
- `dev.samstevens.totp:totp:1.7.1` - Librería para generar y validar códigos TOTP

### 2. **Nuevos Servicios**
- **TotpService.java**: Servicio para generar secretos TOTP, crear QR codes y verificar códigos

### 3. **Nuevos DTOs Creados**
- **TotpSetupResponse.java**: Respuesta con QR code y secreto para configurar Google Authenticator
- **TotpVerifyRequest.java**: Request para verificar códigos TOTP de 6 dígitos
- **TotpLoginRequest.java**: Request para login completo con TOTP (email + password + código)

### 4. **Modificaciones en DTOs Existentes**
- **UserProfileDto**: Agregado campo `totpEnabled` (boolean)
- **LoginResponse**: Agregados campos `totpRequired` y `tempSessionId` para flujo de 2FA

### 5. **Nuevos Endpoints en UserController**

#### 🔐 Configurar TOTP (Usuario Autenticado)
```
POST /users/totp/setup
Authorization: Bearer {token}

Response:
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KG..."
}
```

#### ✅ Verificar y Habilitar TOTP
```
POST /users/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP enabled successfully"
}
```

#### ❌ Deshabilitar TOTP
```
POST /users/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP disabled successfully"
}
```

#### 📊 Consultar Estado TOTP
```
GET /users/totp/status
Authorization: Bearer {token}

Response:
{
  "totpEnabled": true
}
```

#### 🔑 Login con TOTP (Segunda Etapa)
```
POST /users/login/totp?uid={userId}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}
```

### 6. **Modificaciones en UserProfileService**

#### Método `login()` Modificado
Ahora detecta si el usuario tiene TOTP habilitado:
- Si **NO** tiene TOTP: Retorna el token JWT normalmente
- Si **SÍ** tiene TOTP: Retorna `totpRequired: true` y `tempSessionId` sin token

#### Nuevos Métodos TOTP
- `setupTotp(uid)`: Inicia configuración de TOTP
- `verifyAndEnableTotp(uid, code)`: Verifica código y habilita TOTP
- `disableTotp(uid, code)`: Deshabilita TOTP (requiere código válido)
- `loginWithTotp(uid, totpCode)`: Completa el login verificando el código TOTP
- `getTotpEnabled(uid)`: Consulta si TOTP está habilitado

### 7. **Base de Datos (Firestore)**
Se agregaron nuevos campos a la colección `users`:
- `totpEnabled`: boolean (false por defecto)
- `totpSecret`: string (secreto encriptado, null si no está configurado)

## 🔄 Flujo de Uso

### A. Configurar TOTP por Primera Vez

```
1. Usuario inicia sesión normalmente → GET token JWT
2. Usuario llama a POST /users/totp/setup
3. Backend genera secreto y QR code
4. Usuario escanea QR con Google Authenticator
5. Usuario ingresa código de 6 dígitos
6. Usuario llama a POST /users/totp/verify con el código
7. Backend verifica y habilita TOTP
8. ✅ TOTP activado
```

### B. Login con TOTP Habilitado

```
1. Usuario ingresa email + password
2. POST /users/login
3. Backend verifica credenciales
4. Backend detecta que totpEnabled = true
5. Respuesta: { totpRequired: true, tempSessionId: "uid123" }
6. Frontend solicita código TOTP al usuario
7. POST /users/login/totp?uid=uid123 con código
8. Backend verifica código TOTP
9. ✅ Respuesta con token JWT completo
```

### C. Deshabilitar TOTP

```
1. Usuario autenticado llama a POST /users/totp/disable
2. Debe proporcionar código TOTP válido actual
3. Backend verifica código
4. Backend deshabilita TOTP y elimina secreto
5. ✅ TOTP desactivado
```

## 🎨 Implementación en Frontend (Ionic/Angular)

### 1. **Interfaces TypeScript** (interfaces/auth.interface.ts)

```typescript
export interface LoginResponse {
  token?: string;
  user: UserProfile;
  totpRequired: boolean;
  tempSessionId?: string;
}

export interface UserProfile {
  uid: string;
  email: string;
  username: string;
  name: string;
  lastname: string;
  role: string;
  biometricEnabled: boolean;
  totpEnabled: boolean;
}

export interface TotpSetupResponse {
  secret: string;
  qrCodeDataUri: string;
}

export interface TotpVerifyResponse {
  success: boolean;
  message: string;
}

export interface TotpStatusResponse {
  totpEnabled: boolean;
}
```

### 2. **Servicio de Autenticación Completo** (services/auth.service.ts)

```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { 
  LoginResponse, 
  UserProfile, 
  TotpSetupResponse, 
  TotpVerifyResponse,
  TotpStatusResponse 
} from '../interfaces/auth.interface';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/users'; // Ajusta tu URL
  private tokenKey = 'auth_token';
  private userKey = 'user_profile';
  private tempSessionIdKey = 'temp_session_id';

  private currentUserSubject = new BehaviorSubject<UserProfile | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadStoredUser();
  }

  // ==================== MÉTODOS DE LOGIN ====================

  /**
   * Login con usuario y contraseña
   */
  async login(email: string, password: string): Promise<LoginResponse> {
    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      { email, password }
    ).toPromise();

    if (response.totpRequired) {
      // Guardar tempSessionId para la segunda etapa
      localStorage.setItem(this.tempSessionIdKey, response.tempSessionId);
      return response;
    }

    // Login completo sin TOTP
    this.setSession(response.token, response.user);
    return response;
  }

  /**
   * Login con biometría
   */
  async loginWithBiometric(uid: string): Promise<LoginResponse> {
    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login/biometric?uid=${uid}`,
      {}
    ).toPromise();

    if (response.totpRequired) {
      // Guardar tempSessionId para la segunda etapa
      localStorage.setItem(this.tempSessionIdKey, response.tempSessionId);
      return response;
    }

    // Login completo sin TOTP
    this.setSession(response.token, response.user);
    return response;
  }

  /**
   * Completar login con código TOTP
   */
  async verifyTotpLogin(code: string): Promise<LoginResponse> {
    const tempSessionId = localStorage.getItem(this.tempSessionIdKey);
    
    if (!tempSessionId) {
      throw new Error('No hay sesión temporal. Debes iniciar sesión primero.');
    }

    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login/totp?uid=${tempSessionId}`,
      { code }
    ).toPromise();

    // Limpiar tempSessionId
    localStorage.removeItem(this.tempSessionIdKey);

    // Guardar sesión completa
    this.setSession(response.token, response.user);
    return response;
  }

  // ==================== MÉTODOS DE CONFIGURACIÓN TOTP ====================

  /**
   * Iniciar configuración de TOTP (genera QR code)
   */
  async setupTotp(): Promise<TotpSetupResponse> {
    const headers = this.getAuthHeaders();
    return this.http.get<TotpSetupResponse>(
      `${this.apiUrl}/totp/setup`,
      { headers }
    ).toPromise();
  }

  /**
   * Verificar código y habilitar TOTP
   */
  async verifyAndEnableTotp(code: string): Promise<TotpVerifyResponse> {
    const headers = this.getAuthHeaders();
    return this.http.post<TotpVerifyResponse>(
      `${this.apiUrl}/totp/verify`,
      { code },
      { headers }
    ).toPromise();
  }

  /**
   * Deshabilitar TOTP
   */
  async disableTotp(code: string): Promise<TotpVerifyResponse> {
    const headers = this.getAuthHeaders();
    return this.http.post<TotpVerifyResponse>(
      `${this.apiUrl}/totp/disable`,
      { code },
      { headers }
    ).toPromise();
  }

  /**
   * Consultar estado de TOTP
   */
  async getTotpStatus(): Promise<TotpStatusResponse> {
    const headers = this.getAuthHeaders();
    return this.http.get<TotpStatusResponse>(
      `${this.apiUrl}/totp/status`,
      { headers }
    ).toPromise();
  }

  // ==================== MÉTODOS AUXILIARES ====================

  private setSession(token: string, user: UserProfile): void {
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.userKey, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  private loadStoredUser(): void {
    const userStr = localStorage.getItem(this.userKey);
    if (userStr) {
      const user = JSON.parse(userStr);
      this.currentUserSubject.next(user);
    }
  }

  private getAuthHeaders(): HttpHeaders {
    const token = this.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getCurrentUser(): UserProfile | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    localStorage.removeItem(this.tempSessionIdKey);
    this.currentUserSubject.next(null);
  }
}
```

### 3. **Página de Login con TOTP** (pages/login/login.page.ts)

```typescript
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AlertController, LoadingController } from '@ionic/angular';
import { AuthService } from '../../services/auth.service';
import { FingerprintAIO } from '@awesome-cordova-plugins/fingerprint-aio/ngx';

@Component({
  selector: 'app-login',
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
})
export class LoginPage {
  email: string = '';
  password: string = '';
  totpCode: string = '';
  showTotpInput: boolean = false;
  loginMethod: 'password' | 'biometric' | null = null;

  constructor(
    private authService: AuthService,
    private router: Router,
    private alertController: AlertController,
    private loadingController: LoadingController,
    private faio: FingerprintAIO
  ) {}

  /**
   * Login con usuario y contraseña
   */
  async loginWithPassword() {
    const loading = await this.loadingController.create({
      message: 'Iniciando sesión...'
    });
    await loading.present();

    try {
      const response = await this.authService.login(this.email, this.password);
      
      await loading.dismiss();

      if (response.totpRequired) {
        this.showTotpInput = true;
        this.loginMethod = 'password';
        this.showAlert('TOTP Requerido', 'Ingresa el código de Google Authenticator');
      } else {
        this.router.navigate(['/home']);
      }
    } catch (error) {
      await loading.dismiss();
      this.showAlert('Error', 'Credenciales inválidas');
    }
  }

  /**
   * Login con biometría
   */
  async loginWithBiometric() {
    try {
      // Verificar disponibilidad de biometría
      const available = await this.faio.isAvailable();
      
      // Mostrar sensor biométrico
      const result = await this.faio.show({
        title: 'Autenticación Biométrica',
        subtitle: 'Usa tu huella o Face ID',
        fallbackButtonTitle: 'Cancelar',
        disableBackup: true
      });

      if (result) {
        const loading = await this.loadingController.create({
          message: 'Verificando identidad...'
        });
        await loading.present();

        // Obtener UID del usuario (guardado anteriormente)
        const savedUid = localStorage.getItem('biometric_uid');
        
        const response = await this.authService.loginWithBiometric(savedUid);
        
        await loading.dismiss();

        if (response.totpRequired) {
          this.showTotpInput = true;
          this.loginMethod = 'biometric';
          this.showAlert('TOTP Requerido', 'Ingresa el código de Google Authenticator');
        } else {
          this.router.navigate(['/home']);
        }
      }
    } catch (error) {
      this.showAlert('Error', 'Autenticación biométrica fallida');
    }
  }

  /**
   * Verificar código TOTP y completar login
   */
  async verifyTotp() {
    if (this.totpCode.length !== 6) {
      this.showAlert('Error', 'El código debe tener 6 dígitos');
      return;
    }

    const loading = await this.loadingController.create({
      message: 'Verificando código...'
    });
    await loading.present();

    try {
      await this.authService.verifyTotpLogin(this.totpCode);
      await loading.dismiss();
      this.router.navigate(['/home']);
    } catch (error) {
      await loading.dismiss();
      this.showAlert('Error', 'Código TOTP inválido');
      this.totpCode = '';
    }
  }

  private async showAlert(header: string, message: string) {
    const alert = await this.alertController.create({
      header,
      message,
      buttons: ['OK']
    });
    await alert.present();
  }
}
```

### 4. **Template Login HTML** (pages/login/login.page.html)

```html
<ion-header>
  <ion-toolbar>
    <ion-title>Iniciar Sesión</ion-title>
  </ion-toolbar>
</ion-header>

<ion-content class="ion-padding">
  
  <!-- Formulario de Login Normal -->
  <div *ngIf="!showTotpInput">
    <ion-card>
      <ion-card-header>
        <ion-card-title>Bienvenido</ion-card-title>
      </ion-card-header>
      
      <ion-card-content>
        <ion-item>
          <ion-label position="floating">Email</ion-label>
          <ion-input 
            type="email" 
            [(ngModel)]="email"
            autocomplete="email">
          </ion-input>
        </ion-item>

        <ion-item>
          <ion-label position="floating">Contraseña</ion-label>
          <ion-input 
            type="password" 
            [(ngModel)]="password"
            autocomplete="current-password">
          </ion-input>
        </ion-item>

        <ion-button 
          expand="block" 
          (click)="loginWithPassword()"
          [disabled]="!email || !password">
          Iniciar Sesión
        </ion-button>

        <ion-button 
          expand="block" 
          fill="outline"
          (click)="loginWithBiometric()">
          <ion-icon name="finger-print" slot="start"></ion-icon>
          Login con Biometría
        </ion-button>
      </ion-card-content>
    </ion-card>
  </div>

  <!-- Input de Código TOTP -->
  <div *ngIf="showTotpInput">
    <ion-card>
      <ion-card-header>
        <ion-card-title>Autenticación de Dos Factores</ion-card-title>
        <ion-card-subtitle>
          Ingresa el código de 6 dígitos de Google Authenticator
        </ion-card-subtitle>
      </ion-card-header>

      <ion-card-content>
        <ion-item>
          <ion-label position="floating">Código TOTP</ion-label>
          <ion-input 
            type="number" 
            [(ngModel)]="totpCode"
            maxlength="6"
            placeholder="000000">
          </ion-input>
        </ion-item>

        <ion-button 
          expand="block" 
          (click)="verifyTotp()"
          [disabled]="totpCode.length !== 6">
          Verificar Código
        </ion-button>

        <ion-button 
          expand="block" 
          fill="clear"
          (click)="showTotpInput = false; totpCode = ''">
          Cancelar
        </ion-button>
      </ion-card-content>
    </ion-card>
  </div>

</ion-content>
```

### 5. **Página de Configuración TOTP** (pages/totp-setup/totp-setup.page.ts)

```typescript
// Configurar TOTP
async setupTotp() {
  const response = await this.http.get<{secret: string, qrCodeDataUri: string}>(
    `${this.apiUrl}/users/totp/setup`,
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
  
  return response;
}

// Verificar y habilitar TOTP
async verifyTotp(code: string) {
  return this.http.post<{success: boolean, message: string}>(
    `${this.apiUrl}/users/totp/verify`,
    { code },
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
}

// Login modificado
async login(email: string, password: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login`,
    { email, password }
  ).toPromise();
  
  // Si requiere TOTP, guardar tempSessionId
  if (response.totpRequired) {
    this.tempSessionId = response.tempSessionId;
    return { requiresTOTP: true };
  }
  
  // Login normal
  this.token = response.token;
  return { requiresTOTP: false, user: response.user };
}

// Login con TOTP
async loginWithTotp(code: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login/totp?uid=${this.tempSessionId}`,
    { code }
  ).toPromise();
  
  this.token = response.token;
  return response;
}
```

### 2. **Página de Configuración TOTP** (totp-setup.page.ts)

```typescript
export class TotpSetupPage {
  qrCodeImage: string;
  secret: string;
  verificationCode: string = '';

  async setup() {
    const response = await this.authService.setupTotp();
    this.qrCodeImage = response.qrCodeDataUri;
    this.secret = response.secret;
  }

  async verify() {
    const result = await this.authService.verifyTotp(this.verificationCode);
    if (result.success) {
      // Mostrar mensaje de éxito
      this.router.navigate(['/dashboard']);
    }
  }
}
```

### 3. **Template HTML**

```html
<!-- totp-setup.page.html -->
<ion-content>
  <div class="totp-setup">
    <h2>Configurar Autenticación de Dos Factores</h2>
    
    <p>Escanea este código QR con Google Authenticator:</p>
    
    <img [src]="qrCodeImage" alt="QR Code">
    
    <p>O ingresa este código manualmente:</p>
    <code>{{ secret }}</code>
    
    <ion-item>
      <ion-label position="floating">Código de verificación</ion-label>
      <ion-input [(ngModel)]="verificationCode" type="number" maxlength="6"></ion-input>
    </ion-item>
    
    <ion-button (click)="verify()" expand="block">
      Verificar y Activar
    </ion-button>
  </div>
</ion-content>
```

### 4. **Página de Login con TOTP** (login.page.ts)

```typescript
export class LoginPage {
  email: string = '';
  password: string = '';
  totpCode: string = '';
  showTotpInput: boolean = false;

  async login() {
    const result = await this.authService.login(this.email, this.password);
    
    if (result.requiresTOTP) {
      this.showTotpInput = true;
    } else {
      this.router.navigate(['/dashboard']);
    }
  }

  async verifyTotpAndLogin() {
    await this.authService.loginWithTotp(this.totpCode);
    this.router.navigate(['/dashboard']);
  }
}
```

## 🔒 Seguridad

- ✅ Los secretos TOTP se almacenan en Firestore
- ✅ Los códigos tienen ventana de 30 segundos
- ✅ Se permite discrepancia de ±1 período (90 segundos total)
- ✅ El usuario debe estar autenticado para configurar TOTP
- ✅ Se requiere código válido para deshabilitar TOTP
- ✅ Se registran todas las acciones en auditoría

## 📱 Aplicaciones Compatible

- Google Authenticator (iOS/Android)
- Microsoft Authenticator (iOS/Android)
- Authy (iOS/Android/Desktop)
- 1Password
- LastPass Authenticator
- Cualquier app compatible con TOTP RFC 6238

## 🧪 Probar con Postman

1. **Login Normal**: POST `/users/login`
2. **Configurar TOTP**: POST `/users/totp/setup` (con token)
3. Escanear QR con Google Authenticator
4. **Verificar**: POST `/users/totp/verify` con código
5. **Logout y Login de nuevo**: POST `/users/login` → `totpRequired: true`
6. **Completar login**: POST `/users/login/totp?uid={uid}` con código

## ⚠️ Notas Importantes

1. **TOTP es opcional**: El usuario decide si lo habilita (como biometría)
2. **No reemplaza la contraseña**: Es un segundo factor adicional
3. **Compatible con biometría**: Puede tener ambos habilitados
4. **Códigos de 6 dígitos**: Estándar TOTP
5. **30 segundos de validez**: Los códigos cambian cada 30 segundos

## 🚀 Próximos Pasos en Frontend

Ahora necesitas implementar en tu aplicación Ionic:

1. **Pantalla de configuración de TOTP** en el perfil del usuario
2. **Input de código TOTP** en el flujo de login
3. **Indicador visual** de que TOTP está habilitado
4. **Opción para deshabilitar** TOTP (con confirmación)

¿Necesitas ayuda con la implementación en Ionic/Angular?
# Guía de Implementación TOTP (Google Authenticator)

## ✅ Implementación Completada en Backend

Se ha implementado exitosamente la autenticación TOTP (Time-based One-Time Password) en tu microservicio de usuarios de Spring Boot. Esta funcionalidad permite usar aplicaciones como Google Authenticator, Microsoft Authenticator, Authy, etc.

## 📋 Cambios Realizados

### 1. **Dependencias Agregadas** (pom.xml)
- `dev.samstevens.totp:totp:1.7.1` - Librería para generar y validar códigos TOTP

### 2. **Nuevos Servicios**
- **TotpService.java**: Servicio para generar secretos TOTP, crear QR codes y verificar códigos

### 3. **Nuevos DTOs Creados**
- **TotpSetupResponse.java**: Respuesta con QR code y secreto para configurar Google Authenticator
- **TotpVerifyRequest.java**: Request para verificar códigos TOTP de 6 dígitos
- **TotpLoginRequest.java**: Request para login completo con TOTP (email + password + código)

### 4. **Modificaciones en DTOs Existentes**
- **UserProfileDto**: Agregado campo `totpEnabled` (boolean)
- **LoginResponse**: Agregados campos `totpRequired` y `tempSessionId` para flujo de 2FA

### 5. **Nuevos Endpoints en UserController**

#### 🔐 Configurar TOTP (Usuario Autenticado)
```
POST /users/totp/setup
Authorization: Bearer {token}

Response:
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KG..."
}
```

#### ✅ Verificar y Habilitar TOTP
```
POST /users/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP enabled successfully"
}
```

#### ❌ Deshabilitar TOTP
```
POST /users/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP disabled successfully"
}
```

#### 📊 Consultar Estado TOTP
```
GET /users/totp/status
Authorization: Bearer {token}

Response:
{
  "totpEnabled": true
}
```

#### 🔑 Login con TOTP (Segunda Etapa)
```
POST /users/login/totp?uid={userId}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}
```

### 6. **Modificaciones en UserProfileService**

#### Método `login()` Modificado
Ahora detecta si el usuario tiene TOTP habilitado:
- Si **NO** tiene TOTP: Retorna el token JWT normalmente
- Si **SÍ** tiene TOTP: Retorna `totpRequired: true` y `tempSessionId` sin token

#### Nuevos Métodos TOTP
- `setupTotp(uid)`: Inicia configuración de TOTP
- `verifyAndEnableTotp(uid, code)`: Verifica código y habilita TOTP
- `disableTotp(uid, code)`: Deshabilita TOTP (requiere código válido)
- `loginWithTotp(uid, totpCode)`: Completa el login verificando el código TOTP
- `getTotpEnabled(uid)`: Consulta si TOTP está habilitado

### 7. **Base de Datos (Firestore)**
Se agregaron nuevos campos a la colección `users`:
- `totpEnabled`: boolean (false por defecto)
- `totpSecret`: string (secreto encriptado, null si no está configurado)

## 🔄 Flujo de Uso

### A. Configurar TOTP por Primera Vez

```
1. Usuario inicia sesión normalmente → GET token JWT
2. Usuario llama a POST /users/totp/setup
3. Backend genera secreto y QR code
4. Usuario escanea QR con Google Authenticator
5. Usuario ingresa código de 6 dígitos
6. Usuario llama a POST /users/totp/verify con el código
7. Backend verifica y habilita TOTP
8. ✅ TOTP activado
```

### B. Login con Usuario/Contraseña + TOTP

```
1. Usuario ingresa email + password
2. POST /users/login
3. Backend verifica credenciales
4. Backend detecta que totpEnabled = true
5. Respuesta: { totpRequired: true, tempSessionId: "uid123" }
6. Frontend muestra input para código TOTP
7. Usuario ingresa código de Google Authenticator
8. POST /users/login/totp?uid=uid123 con código
9. Backend verifica código TOTP
10. ✅ Respuesta con token JWT completo
```

### C. Login con Biometría + TOTP

```
1. Usuario activa sensor biométrico (huella/face)
2. POST /users/login/biometric?uid={userId}
3. Backend verifica que biometría esté habilitada
4. Backend detecta que totpEnabled = true
5. Respuesta: { totpRequired: true, tempSessionId: "uid123" }
6. Frontend muestra input para código TOTP
7. Usuario ingresa código de Google Authenticator
8. POST /users/login/totp?uid=uid123 con código
9. Backend verifica código TOTP
10. ✅ Respuesta con token JWT completo
```

### D. Deshabilitar TOTP

```
1. Usuario autenticado llama a POST /users/totp/disable
2. Debe proporcionar código TOTP válido actual
3. Backend verifica código
4. Backend deshabilita TOTP y elimina secreto
5. ✅ TOTP desactivado
```

## 🎨 Implementación en Frontend (Ionic/Angular)

### 1. **Servicio de Autenticación** (auth.service.ts)

```typescript
// Configurar TOTP
async setupTotp() {
  const response = await this.http.get<{secret: string, qrCodeDataUri: string}>(
    `${this.apiUrl}/users/totp/setup`,
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
  
  return response;
}

// Verificar y habilitar TOTP
async verifyTotp(code: string) {
  return this.http.post<{success: boolean, message: string}>(
    `${this.apiUrl}/users/totp/verify`,
    { code },
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
}

// Login modificado
async login(email: string, password: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login`,
    { email, password }
  ).toPromise();
  
  // Si requiere TOTP, guardar tempSessionId
  if (response.totpRequired) {
    this.tempSessionId = response.tempSessionId;
    return { requiresTOTP: true };
  }
  
  // Login normal
  this.token = response.token;
  return { requiresTOTP: false, user: response.user };
}

// Login con TOTP
async loginWithTotp(code: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login/totp?uid=${this.tempSessionId}`,
    { code }
  ).toPromise();
  
  this.token = response.token;
  return response;
}
```

### 2. **Página de Configuración TOTP** (totp-setup.page.ts)

```typescript
export class TotpSetupPage {
  qrCodeImage: string;
  secret: string;
  verificationCode: string = '';

  async setup() {
    const response = await this.authService.setupTotp();
    this.qrCodeImage = response.qrCodeDataUri;
    this.secret = response.secret;
  }

  async verify() {
    const result = await this.authService.verifyTotp(this.verificationCode);
    if (result.success) {
      // Mostrar mensaje de éxito
      this.router.navigate(['/dashboard']);
    }
  }
}
```

### 3. **Template HTML**

```html
<!-- totp-setup.page.html -->
<ion-content>
  <div class="totp-setup">
    <h2>Configurar Autenticación de Dos Factores</h2>
    
    <p>Escanea este código QR con Google Authenticator:</p>
    
    <img [src]="qrCodeImage" alt="QR Code">
    
    <p>O ingresa este código manualmente:</p>
    <code>{{ secret }}</code>
    
    <ion-item>
      <ion-label position="floating">Código de verificación</ion-label>
      <ion-input [(ngModel)]="verificationCode" type="number" maxlength="6"></ion-input>
    </ion-item>
    
    <ion-button (click)="verify()" expand="block">
      Verificar y Activar
    </ion-button>
  </div>
</ion-content>
```

### 4. **Página de Login con TOTP** (login.page.ts)

```typescript
export class LoginPage {
  email: string = '';
  password: string = '';
  totpCode: string = '';
  showTotpInput: boolean = false;

  async login() {
    const result = await this.authService.login(this.email, this.password);
    
    if (result.requiresTOTP) {
      this.showTotpInput = true;
    } else {
      this.router.navigate(['/dashboard']);
    }
  }

  async verifyTotpAndLogin() {
    await this.authService.loginWithTotp(this.totpCode);
    this.router.navigate(['/dashboard']);
  }
}
```

## 🔒 Seguridad

- ✅ Los secretos TOTP se almacenan en Firestore
- ✅ Los códigos tienen ventana de 30 segundos
- ✅ Se permite discrepancia de ±1 período (90 segundos total)
- ✅ El usuario debe estar autenticado para configurar TOTP
- ✅ Se requiere código válido para deshabilitar TOTP
- ✅ Se registran todas las acciones en auditoría

## 📱 Aplicaciones Compatible

- Google Authenticator (iOS/Android)
- Microsoft Authenticator (iOS/Android)
- Authy (iOS/Android/Desktop)
- 1Password
- LastPass Authenticator
- Cualquier app compatible con TOTP RFC 6238

## 🧪 Probar con Postman

1. **Login Normal**: POST `/users/login`
2. **Configurar TOTP**: POST `/users/totp/setup` (con token)
3. Escanear QR con Google Authenticator
4. **Verificar**: POST `/users/totp/verify` con código
5. **Logout y Login de nuevo**: POST `/users/login` → `totpRequired: true`
6. **Completar login**: POST `/users/login/totp?uid={uid}` con código

## ⚠️ Notas Importantes

1. **TOTP es opcional**: El usuario decide si lo habilita (como biometría)
2. **No reemplaza la contraseña**: Es un segundo factor adicional
3. **Compatible con biometría**: Puede tener ambos habilitados
4. **Códigos de 6 dígitos**: Estándar TOTP
5. **30 segundos de validez**: Los códigos cambian cada 30 segundos

## 🚀 Próximos Pasos en Frontend

Ahora necesitas implementar en tu aplicación Ionic:

1. **Pantalla de configuración de TOTP** en el perfil del usuario
2. **Input de código TOTP** en el flujo de login
3. **Indicador visual** de que TOTP está habilitado
4. **Opción para deshabilitar** TOTP (con confirmación)

¿Necesitas ayuda con la implementación en Ionic/Angular?
# Guía de Implementación TOTP (Google Authenticator)

## ✅ Implementación Completada en Backend

Se ha implementado exitosamente la autenticación TOTP (Time-based One-Time Password) en tu microservicio de usuarios de Spring Boot. Esta funcionalidad permite usar aplicaciones como Google Authenticator, Microsoft Authenticator, Authy, etc.

## 📋 Cambios Realizados

### 1. **Dependencias Agregadas** (pom.xml)
- `dev.samstevens.totp:totp:1.7.1` - Librería para generar y validar códigos TOTP

### 2. **Nuevos Servicios**
- **TotpService.java**: Servicio para generar secretos TOTP, crear QR codes y verificar códigos

### 3. **Nuevos DTOs Creados**
- **TotpSetupResponse.java**: Respuesta con QR code y secreto para configurar Google Authenticator
- **TotpVerifyRequest.java**: Request para verificar códigos TOTP de 6 dígitos
- **TotpLoginRequest.java**: Request para login completo con TOTP (email + password + código)

### 4. **Modificaciones en DTOs Existentes**
- **UserProfileDto**: Agregado campo `totpEnabled` (boolean)
- **LoginResponse**: Agregados campos `totpRequired` y `tempSessionId` para flujo de 2FA

### 5. **Nuevos Endpoints en UserController**

#### 🔐 Configurar TOTP (Usuario Autenticado)
```
POST /users/totp/setup
Authorization: Bearer {token}

Response:
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KG..."
}
```

#### ✅ Verificar y Habilitar TOTP
```
POST /users/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP enabled successfully"
}
```

#### ❌ Deshabilitar TOTP
```
POST /users/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP disabled successfully"
}
```

#### 📊 Consultar Estado TOTP
```
GET /users/totp/status
Authorization: Bearer {token}

Response:
{
  "totpEnabled": true
}
```

#### 🔑 Login con TOTP (Segunda Etapa)
```
POST /users/login/totp?uid={userId}
Content-Type: application/json

{
  "code": "123456"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}
```

#### 🆕 Login con Biometría (Público)
```
POST /users/login/biometric?uid={userId}
Content-Type: application/json

Response (sin TOTP):
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}

Response (con TOTP habilitado):
{
  "token": null,
  "user": { ... },
  "totpRequired": true,
  "tempSessionId": "userId"
}
```

### 6. **Modificaciones en UserProfileService**

#### Método `login()` Modificado
Ahora detecta si el usuario tiene TOTP habilitado:
- Si **NO** tiene TOTP: Retorna el token JWT normalmente
- Si **SÍ** tiene TOTP: Retorna `totpRequired: true` y `tempSessionId` sin token

#### Nuevos Métodos TOTP
- `setupTotp(uid)`: Inicia configuración de TOTP
- `verifyAndEnableTotp(uid, code)`: Verifica código y habilita TOTP
- `disableTotp(uid, code)`: Deshabilita TOTP (requiere código válido)
- `loginWithTotp(uid, totpCode)`: Completa el login verificando el código TOTP
- `getTotpEnabled(uid)`: Consulta si TOTP está habilitado

### 7. **Base de Datos (Firestore)**
Se agregaron nuevos campos a la colección `users`:
- `totpEnabled`: boolean (false por defecto)
- `totpSecret`: string (secreto encriptado, null si no está configurado)

## 🔄 Flujo de Uso

### A. Configurar TOTP por Primera Vez

```
1. Usuario inicia sesión normalmente → GET token JWT
2. Usuario llama a POST /users/totp/setup
3. Backend genera secreto y QR code
4. Usuario escanea QR con Google Authenticator
5. Usuario ingresa código de 6 dígitos
6. Usuario llama a POST /users/totp/verify con el código
7. Backend verifica y habilita TOTP
8. ✅ TOTP activado
```

### B. Login con TOTP Habilitado

```
1. Usuario ingresa email + password
2. POST /users/login
3. Backend verifica credenciales
4. Backend detecta que totpEnabled = true
5. Respuesta: { totpRequired: true, tempSessionId: "uid123" }
6. Frontend solicita código TOTP al usuario
7. POST /users/login/totp?uid=uid123 con código
8. Backend verifica código TOTP
9. ✅ Respuesta con token JWT completo
```

### C. Deshabilitar TOTP

```
1. Usuario autenticado llama a POST /users/totp/disable
2. Debe proporcionar código TOTP válido actual
3. Backend verifica código
4. Backend deshabilita TOTP y elimina secreto
5. ✅ TOTP desactivado
```

## 🎨 Implementación en Frontend (Ionic/Angular)

### 1. **Servicio de Autenticación** (auth.service.ts)

```typescript
// Configurar TOTP
async setupTotp() {
  const response = await this.http.get<{secret: string, qrCodeDataUri: string}>(
    `${this.apiUrl}/users/totp/setup`,
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
  
  return response;
}

// Verificar y habilitar TOTP
async verifyTotp(code: string) {
  return this.http.post<{success: boolean, message: string}>(
    `${this.apiUrl}/users/totp/verify`,
    { code },
    { headers: { Authorization: `Bearer ${this.token}` } }
  ).toPromise();
}

// Login modificado
async login(email: string, password: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login`,
    { email, password }
  ).toPromise();
  
  // Si requiere TOTP, guardar tempSessionId
  if (response.totpRequired) {
    this.tempSessionId = response.tempSessionId;
    return { requiresTOTP: true };
  }
  
  // Login normal
  this.token = response.token;
  return { requiresTOTP: false, user: response.user };
}

// Login con TOTP
async loginWithTotp(code: string) {
  const response = await this.http.post<LoginResponse>(
    `${this.apiUrl}/users/login/totp?uid=${this.tempSessionId}`,
    { code }
  ).toPromise();
  
  this.token = response.token;
  return response;
}
```

### 2. **Página de Configuración TOTP** (totp-setup.page.ts)

```typescript
export class TotpSetupPage {
  qrCodeImage: string;
  secret: string;
  verificationCode: string = '';

  async setup() {
    const response = await this.authService.setupTotp();
    this.qrCodeImage = response.qrCodeDataUri;
    this.secret = response.secret;
  }

  async verify() {
    const result = await this.authService.verifyTotp(this.verificationCode);
    if (result.success) {
      // Mostrar mensaje de éxito
      this.router.navigate(['/dashboard']);
    }
  }
}
```

### 3. **Template HTML**

```html
<!-- totp-setup.page.html -->
<ion-content>
  <div class="totp-setup">
    <h2>Configurar Autenticación de Dos Factores</h2>
    
    <p>Escanea este código QR con Google Authenticator:</p>
    
    <img [src]="qrCodeImage" alt="QR Code">
    
    <p>O ingresa este código manualmente:</p>
    <code>{{ secret }}</code>
    
    <ion-item>
      <ion-label position="floating">Código de verificación</ion-label>
      <ion-input [(ngModel)]="verificationCode" type="number" maxlength="6"></ion-input>
    </ion-item>
    
    <ion-button (click)="verify()" expand="block">
      Verificar y Activar
    </ion-button>
  </div>
</ion-content>
```

### 4. **Página de Login con TOTP** (login.page.ts)

```typescript
export class LoginPage {
  email: string = '';
  password: string = '';
  totpCode: string = '';
  showTotpInput: boolean = false;

  async login() {
    const result = await this.authService.login(this.email, this.password);
    
    if (result.requiresTOTP) {
      this.showTotpInput = true;
    } else {
      this.router.navigate(['/dashboard']);
    }
  }

  async verifyTotpAndLogin() {
    await this.authService.loginWithTotp(this.totpCode);
    this.router.navigate(['/dashboard']);
  }
}
```

## 🔒 Seguridad

- ✅ Los secretos TOTP se almacenan en Firestore
- ✅ Los códigos tienen ventana de 30 segundos
- ✅ Se permite discrepancia de ±1 período (90 segundos total)
- ✅ El usuario debe estar autenticado para configurar TOTP
- ✅ Se requiere código válido para deshabilitar TOTP
- ✅ Se registran todas las acciones en auditoría

## 📱 Aplicaciones Compatible

- Google Authenticator (iOS/Android)
- Microsoft Authenticator (iOS/Android)
- Authy (iOS/Android/Desktop)
- 1Password
- LastPass Authenticator
- Cualquier app compatible con TOTP RFC 6238

## 🧪 Probar con Postman

1. **Login Normal**: POST `/users/login`
2. **Configurar TOTP**: POST `/users/totp/setup` (con token)
3. Escanear QR con Google Authenticator
4. **Verificar**: POST `/users/totp/verify` con código
5. **Logout y Login de nuevo**: POST `/users/login` → `totpRequired: true`
6. **Completar login**: POST `/users/login/totp?uid={uid}` con código

## ⚠️ Notas Importantes

1. **TOTP es opcional**: El usuario decide si lo habilita (como biometría)
2. **No reemplaza la contraseña**: Es un segundo factor adicional
3. **Compatible con biometría**: Puede tener ambos habilitados
4. **Códigos de 6 dígitos**: Estándar TOTP
5. **30 segundos de validez**: Los códigos cambian cada 30 segundos

## 🚀 Próximos Pasos en Frontend

Ahora necesitas implementar en tu aplicación Ionic:

1. **Pantalla de configuración de TOTP** en el perfil del usuario
2. **Input de código TOTP** en el flujo de login
3. **Indicador visual** de que TOTP está habilitado
4. **Opción para deshabilitar** TOTP (con confirmación)

¿Necesitas ayuda con la implementación en Ionic/Angular?

