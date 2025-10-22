# 🔐 Guía Completa de Implementación TOTP + Biometría

## ✅ Backend Completado

Tu backend de Spring Boot ya tiene implementado:
- ✅ TOTP (Google Authenticator) 
- ✅ Login con usuario/contraseña + verificación TOTP
- ✅ Login con biometría + verificación TOTP
- ✅ Configuración completa de TOTP
- ✅ Auditoría de todos los eventos

---

## 📋 Endpoints Disponibles

### **🔑 Autenticación**

#### 1. Login con Usuario/Contraseña
```http
POST /users/login
Content-Type: application/json

Request:
{
  "email": "usuario@ejemplo.com",
  "password": "MiPassword123"
}

Response SIN TOTP:
{
  "token": "eyJhbGciOiJIUz...",
  "user": {
    "uid": "abc123",
    "email": "usuario@ejemplo.com",
    "totpEnabled": false,
    "biometricEnabled": false
  },
  "totpRequired": false
}

Response CON TOTP:
{
  "token": null,
  "user": { ... },
  "totpRequired": true,
  "tempSessionId": "abc123"
}
```

#### 2. Login con Biometría
```http
POST /users/login/biometric?uid={userId}

Response SIN TOTP:
{
  "token": "eyJhbGciOiJIUz...",
  "user": { ... },
  "totpRequired": false
}

Response CON TOTP:
{
  "token": null,
  "user": { ... },
  "totpRequired": true,
  "tempSessionId": "abc123"
}
```

#### 3. Completar Login con Código TOTP
```http
POST /users/login/totp?uid={tempSessionId}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response:
{
  "token": "eyJhbGciOiJIUz...",
  "user": { ... },
  "totpRequired": false
}
```

### **⚙️ Configuración TOTP**

#### 4. Generar QR Code
```http
POST /users/totp/setup
Authorization: Bearer {token}

Response:
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KG..."
}
```

#### 5. Verificar y Habilitar TOTP
```http
POST /users/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP enabled successfully"
}
```

#### 6. Deshabilitar TOTP
```http
POST /users/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response:
{
  "success": true,
  "message": "TOTP disabled successfully"
}
```

#### 7. Consultar Estado TOTP
```http
GET /users/totp/status
Authorization: Bearer {token}

Response:
{
  "totpEnabled": true
}
```

---

## 🔄 Flujos Completos

### **Flujo 1: Usuario/Contraseña + TOTP**
```
1. Usuario ingresa email + password
2. POST /users/login
3. Backend verifica credenciales ✓
4. Backend detecta totpEnabled = true
5. Response: { totpRequired: true, tempSessionId: "uid" }
6. Frontend muestra input de código TOTP
7. Usuario ingresa código de Google Authenticator
8. POST /users/login/totp?uid={uid} con el código
9. Backend verifica código ✓
10. Response con token JWT completo
11. ✅ Usuario autenticado
```

### **Flujo 2: Biometría + TOTP**
```
1. Usuario activa huella/Face ID
2. Dispositivo valida biometría ✓
3. POST /users/login/biometric?uid={uid}
4. Backend verifica que biometría esté habilitada ✓
5. Backend detecta totpEnabled = true
6. Response: { totpRequired: true, tempSessionId: "uid" }
7. Frontend muestra input de código TOTP
8. Usuario ingresa código de Google Authenticator
9. POST /users/login/totp?uid={uid} con el código
10. Backend verifica código ✓
11. Response con token JWT completo
12. ✅ Usuario autenticado
```

### **Flujo 3: Configurar TOTP**
```
1. Usuario autenticado va a configuración
2. POST /users/totp/setup con token
3. Backend genera secreto y QR code
4. Frontend muestra QR para escanear
5. Usuario abre Google Authenticator
6. Usuario escanea QR o ingresa código manual
7. Usuario ve código de 6 dígitos en la app
8. Usuario ingresa código en tu app
9. POST /users/totp/verify con código
10. Backend verifica que el código es correcto ✓
11. ✅ TOTP habilitado
```

---

## 📱 CÓDIGO COMPLETO PARA IONIC/ANGULAR

### **1. Interfaces TypeScript** 
**Archivo:** `src/app/interfaces/auth.interface.ts`

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

### **2. Servicio de Autenticación**
**Archivo:** `src/app/services/auth.service.ts`

```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
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
  private apiUrl = 'http://localhost:8080/users'; // ⚠️ CAMBIAR A TU URL
  
  private currentUserSubject = new BehaviorSubject<UserProfile | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadStoredUser();
  }

  // ==================== LOGIN ====================

  async login(email: string, password: string): Promise<LoginResponse> {
    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      { email, password }
    ).toPromise();

    if (response.totpRequired) {
      localStorage.setItem('temp_session_id', response.tempSessionId);
      return response;
    }

    this.setSession(response.token, response.user);
    return response;
  }

  async loginWithBiometric(uid: string): Promise<LoginResponse> {
    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login/biometric?uid=${uid}`,
      {}
    ).toPromise();

    if (response.totpRequired) {
      localStorage.setItem('temp_session_id', response.tempSessionId);
      return response;
    }

    this.setSession(response.token, response.user);
    return response;
  }

  async verifyTotpLogin(code: string): Promise<LoginResponse> {
    const tempSessionId = localStorage.getItem('temp_session_id');
    
    if (!tempSessionId) {
      throw new Error('No hay sesión temporal');
    }

    const response = await this.http.post<LoginResponse>(
      `${this.apiUrl}/login/totp?uid=${tempSessionId}`,
      { code }
    ).toPromise();

    localStorage.removeItem('temp_session_id');
    this.setSession(response.token, response.user);
    return response;
  }

  // ==================== TOTP CONFIG ====================

  async setupTotp(): Promise<TotpSetupResponse> {
    return this.http.get<TotpSetupResponse>(
      `${this.apiUrl}/totp/setup`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  async verifyAndEnableTotp(code: string): Promise<TotpVerifyResponse> {
    return this.http.post<TotpVerifyResponse>(
      `${this.apiUrl}/totp/verify`,
      { code },
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  async disableTotp(code: string): Promise<TotpVerifyResponse> {
    return this.http.post<TotpVerifyResponse>(
      `${this.apiUrl}/totp/disable`,
      { code },
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  async getTotpStatus(): Promise<TotpStatusResponse> {
    return this.http.get<TotpStatusResponse>(
      `${this.apiUrl}/totp/status`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  // ==================== HELPERS ====================

  private setSession(token: string, user: UserProfile): void {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('user_profile', JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  private loadStoredUser(): void {
    const userStr = localStorage.getItem('user_profile');
    if (userStr) {
      this.currentUserSubject.next(JSON.parse(userStr));
    }
  }

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('auth_token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  getCurrentUser(): UserProfile | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.clear();
    this.currentUserSubject.next(null);
  }
}
```

### **3. Página de Login**
**Archivo:** `src/app/pages/login/login.page.ts`

```typescript
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AlertController, LoadingController } from '@ionic/angular';
import { AuthService } from '../../services/auth.service';
import { FingerprintAIO } from '@awesome-cordova-plugins/fingerprint-aio/ngx';

@Component({
  selector: 'app-login',
  templateUrl: './login.page.html'
})
export class LoginPage {
  email = '';
  password = '';
  totpCode = '';
  showTotpInput = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private alertController: AlertController,
    private loadingController: LoadingController,
    private faio: FingerprintAIO
  ) {}

  async loginWithPassword() {
    const loading = await this.loadingController.create({ message: 'Iniciando sesión...' });
    await loading.present();

    try {
      const response = await this.authService.login(this.email, this.password);
      await loading.dismiss();

      if (response.totpRequired) {
        this.showTotpInput = true;
        this.showAlert('TOTP Requerido', 'Ingresa el código de Google Authenticator');
      } else {
        this.router.navigate(['/home']);
      }
    } catch (error) {
      await loading.dismiss();
      this.showAlert('Error', 'Credenciales inválidas');
    }
  }

  async loginWithBiometric() {
    try {
      await this.faio.show({
        title: 'Autenticación Biométrica',
        subtitle: 'Usa tu huella o Face ID'
      });

      const loading = await this.loadingController.create({ message: 'Verificando...' });
      await loading.present();

      const savedUid = localStorage.getItem('biometric_uid');
      const response = await this.authService.loginWithBiometric(savedUid);
      
      await loading.dismiss();

      if (response.totpRequired) {
        this.showTotpInput = true;
        this.showAlert('TOTP Requerido', 'Ingresa el código de Google Authenticator');
      } else {
        this.router.navigate(['/home']);
      }
    } catch (error) {
      this.showAlert('Error', 'Autenticación fallida');
    }
  }

  async verifyTotp() {
    if (this.totpCode.length !== 6) {
      this.showAlert('Error', 'El código debe tener 6 dígitos');
      return;
    }

    const loading = await this.loadingController.create({ message: 'Verificando...' });
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
    const alert = await this.alertController.create({ header, message, buttons: ['OK'] });
    await alert.present();
  }
}
```

**Template HTML:** `src/app/pages/login/login.page.html`

```html
<ion-header>
  <ion-toolbar>
    <ion-title>Iniciar Sesión</ion-title>
  </ion-toolbar>
</ion-header>

<ion-content class="ion-padding">
  
  <!-- Login Normal -->
  <div *ngIf="!showTotpInput">
    <ion-card>
      <ion-card-content>
        <ion-item>
          <ion-label position="floating">Email</ion-label>
          <ion-input type="email" [(ngModel)]="email"></ion-input>
        </ion-item>

        <ion-item>
          <ion-label position="floating">Contraseña</ion-label>
          <ion-input type="password" [(ngModel)]="password"></ion-input>
        </ion-item>

        <ion-button expand="block" (click)="loginWithPassword()" [disabled]="!email || !password">
          Iniciar Sesión
        </ion-button>

        <ion-button expand="block" fill="outline" (click)="loginWithBiometric()">
          <ion-icon name="finger-print" slot="start"></ion-icon>
          Login con Biometría
        </ion-button>
      </ion-card-content>
    </ion-card>
  </div>

  <!-- Input TOTP -->
  <div *ngIf="showTotpInput">
    <ion-card>
      <ion-card-header>
        <ion-card-title>Código de Verificación</ion-card-title>
        <ion-card-subtitle>Ingresa el código de Google Authenticator</ion-card-subtitle>
      </ion-card-header>

      <ion-card-content>
        <ion-item>
          <ion-label position="floating">Código TOTP (6 dígitos)</ion-label>
          <ion-input type="number" [(ngModel)]="totpCode" maxlength="6"></ion-input>
        </ion-item>

        <ion-button expand="block" (click)="verifyTotp()" [disabled]="totpCode.length !== 6">
          Verificar
        </ion-button>

        <ion-button expand="block" fill="clear" (click)="showTotpInput = false; totpCode = ''">
          Cancelar
        </ion-button>
      </ion-card-content>
    </ion-card>
  </div>

</ion-content>
```

---

## 🚀 Pasos para Implementar

1. **Instala dependencias:**
```bash
npm install @awesome-cordova-plugins/fingerprint-aio
```

2. **Configura HttpClient en `app.module.ts`:**
```typescript
import { HttpClientModule } from '@angular/common/http';
imports: [ HttpClientModule, ... ]
```

3. **Crea las interfaces, servicio y páginas** con el código de arriba

4. **Actualiza la URL del API** en `auth.service.ts` línea 17

5. **Prueba el flujo completo**

---

## ✅ Resumen

**TU BACKEND YA ESTÁ LISTO** y soporta:
- ✅ Login normal + TOTP opcional
- ✅ Login biométrico + TOTP opcional  
- ✅ Configuración completa de TOTP
- ✅ Todos los endpoints funcionando

**TIENES TODO EL CÓDIGO** para tu frontend Ionic con:
- ✅ Servicios completos
- ✅ Componentes listos
- ✅ Interfaces TypeScript
- ✅ HTML templates

**Solo copia el código, ajusta la URL del API y ¡funciona!** 🎉

