# 📚 API Endpoints Reference - Etikos User Service

## 🌐 Base URL
```
http://localhost:8080/users
```
⚠️ Cambia esto por tu URL de producción cuando despliegues.

---

## 📑 Tabla de Contenidos
- [Endpoints Públicos (Sin Autenticación)](#-endpoints-públicos)
- [Endpoints de Usuario Autenticado](#-endpoints-de-usuario-autenticado)
- [Endpoints de Administrador](#-endpoints-de-administrador)
- [Código Frontend para Consumir API](#-código-frontend-ionic)

---

## 🔓 Endpoints Públicos

### 1. **Registro de Usuario**
```http
POST /users/register
Content-Type: application/json

Request:
{
  "email": "usuario@ejemplo.com",
  "username": "usuario123",
  "password": "Password123!",
  "name": "Juan",
  "lastname": "Pérez"
}

Response: 200 OK
{
  "uid": "abc123",
  "email": "usuario@ejemplo.com",
  "username": "usuario123",
  "name": "Juan",
  "lastname": "Pérez",
  "role": "CUSTOMER",
  "biometricEnabled": false,
  "totpEnabled": false
}
```

### 2. **Login con Usuario/Contraseña**
```http
POST /users/login
Content-Type: application/json

Request:
{
  "email": "usuario@ejemplo.com",
  "password": "Password123!"
}

Response SIN TOTP: 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "uid": "abc123",
    "email": "usuario@ejemplo.com",
    "username": "usuario123",
    "name": "Juan",
    "lastname": "Pérez",
    "role": "CUSTOMER",
    "biometricEnabled": false,
    "totpEnabled": false
  },
  "totpRequired": false
}

Response CON TOTP: 200 OK
{
  "token": null,
  "user": { ... },
  "totpRequired": true,
  "tempSessionId": "abc123"
}
```

### 3. **Login con Biometría**
```http
POST /users/login/biometric?uid={userId}

Response SIN TOTP: 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}

Response CON TOTP: 200 OK
{
  "token": null,
  "user": { ... },
  "totpRequired": true,
  "tempSessionId": "abc123"
}
```

### 4. **Verificar Código TOTP (Segunda Etapa de Login)**
```http
POST /users/login/totp?uid={tempSessionId}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response: 200 OK
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": { ... },
  "totpRequired": false
}

Errors:
- 401 Unauthorized: Código TOTP inválido
- 500 Internal Server Error: Error del servidor
```

### 5. **Solicitar Restablecimiento de Contraseña**
```http
POST /users/password-reset?email={email}

Response: 200 OK
{
  "message": "Password reset link will be sent to email (not implemented yet)"
}
```

### 6. **Auditar Logout**
```http
POST /users/audit/logout
Authorization: Bearer {token} (opcional)

Response: 200 OK
{
  "message": "Logout successful"
}
```

---

## 👤 Endpoints de Usuario Autenticado

**Todos estos endpoints requieren:**
```
Authorization: Bearer {token}
```

### 1. **Configurar TOTP (Generar QR Code)**
```http
POST /users/totp/setup
Authorization: Bearer {token}

Response: 200 OK
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KG..."
}
```

### 2. **Verificar y Habilitar TOTP**
```http
POST /users/totp/verify
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response: 200 OK
{
  "success": true,
  "message": "TOTP enabled successfully"
}

Response: 400 Bad Request
{
  "success": false,
  "message": "Invalid TOTP code"
}
```

### 3. **Deshabilitar TOTP**
```http
POST /users/totp/disable
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "code": "123456"
}

Response: 200 OK
{
  "success": true,
  "message": "TOTP disabled successfully"
}
```

### 4. **Consultar Estado de TOTP**
```http
GET /users/totp/status
Authorization: Bearer {token}

Response: 200 OK
{
  "totpEnabled": true
}
```

### 5. **Actualizar Preferencia de Biometría**
```http
PUT /users/biometric
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "enabled": true
}

Response: 200 OK
```

### 6. **Consultar Preferencia de Biometría**
```http
GET /users/{uid}/biometric
Authorization: Bearer {token}

Response: 200 OK
{
  "enabled": true
}

Note: Solo el propio usuario o un admin puede consultar esta información
```

---

## 🛡️ Endpoints de Administrador

**Todos estos endpoints requieren:**
```
Authorization: Bearer {token}
Role: ADMIN
```

### 1. **Listar Todos los Usuarios**
```http
GET /users
Authorization: Bearer {admin_token}

Response: 200 OK
[
  {
    "uid": "abc123",
    "email": "usuario1@ejemplo.com",
    "username": "usuario1",
    "name": "Juan",
    "lastname": "Pérez",
    "role": "CUSTOMER",
    "biometricEnabled": false,
    "totpEnabled": true
  },
  {
    "uid": "def456",
    "email": "usuario2@ejemplo.com",
    "username": "usuario2",
    "name": "María",
    "lastname": "García",
    "role": "CUSTOMER",
    "biometricEnabled": true,
    "totpEnabled": false
  }
]
```

### 2. **Obtener Usuario por ID**
```http
GET /users/{uid}
Authorization: Bearer {admin_token}

Response: 200 OK
{
  "uid": "abc123",
  "email": "usuario@ejemplo.com",
  "username": "usuario123",
  "name": "Juan",
  "lastname": "Pérez",
  "role": "CUSTOMER",
  "biometricEnabled": false,
  "totpEnabled": true
}

Errors:
- 403 Forbidden: No eres administrador
- 404 Not Found: Usuario no encontrado
```

### 3. **Actualizar Credenciales de Usuario**
```http
PUT /users/{uid}/credentials
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "newEmail": "nuevo@ejemplo.com",
  "newPassword": "NuevaPassword123!"
}

Response: 200 OK

Note: Ambos campos son opcionales, puedes enviar solo uno
```

### 4. **Bloquear/Desbloquear Usuario**
```http
PUT /users/{uid}/block
Authorization: Bearer {admin_token}
Content-Type: application/json

Request (Bloquear):
{
  "disabled": true
}

Request (Desbloquear):
{
  "disabled": false
}

Response: 200 OK
```

### 5. **Eliminar Usuario**
```http
DELETE /users/{uid}
Authorization: Bearer {admin_token}

Response: 200 OK

Errors:
- 403 Forbidden: No eres administrador
- 404 Not Found: Usuario no encontrado
```

### 6. **Listar Estado Biométrico de Todos los Usuarios**
```http
GET /users/biometric-status
Authorization: Bearer {admin_token}

Response: 200 OK
[
  {
    "uid": "abc123",
    "email": "usuario1@ejemplo.com",
    "username": "usuario1",
    "biometricEnabled": true
  },
  {
    "uid": "def456",
    "email": "usuario2@ejemplo.com",
    "username": "usuario2",
    "biometricEnabled": false
  }
]
```

---

## 💻 Código Frontend (Ionic/Angular)

### **Servicio Admin Completo**

```typescript
// services/admin.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from './auth.service';

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

export interface UpdateCredentialsRequest {
  newEmail?: string;
  newPassword?: string;
}

export interface BlockRequest {
  disabled: boolean;
}

export interface BiometricStatus {
  uid: string;
  email: string;
  username: string;
  biometricEnabled: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private apiUrl = 'http://localhost:8080/users'; // ⚠️ CAMBIAR A TU URL

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  // ==================== LISTAR Y OBTENER USUARIOS ====================

  /**
   * Obtiene la lista completa de usuarios (solo admin)
   */
  async getAllUsers(): Promise<UserProfile[]> {
    return this.http.get<UserProfile[]>(
      `${this.apiUrl}`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  /**
   * Obtiene un usuario por su UID (solo admin)
   */
  async getUserById(uid: string): Promise<UserProfile> {
    return this.http.get<UserProfile>(
      `${this.apiUrl}/${uid}`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  // ==================== ACTUALIZAR CREDENCIALES ====================

  /**
   * Actualiza email y/o contraseña de un usuario (solo admin)
   */
  async updateUserCredentials(
    uid: string, 
    credentials: UpdateCredentialsRequest
  ): Promise<void> {
    return this.http.put<void>(
      `${this.apiUrl}/${uid}/credentials`,
      credentials,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  // ==================== BLOQUEAR/DESBLOQUEAR ====================

  /**
   * Bloquea o desbloquea un usuario (solo admin)
   */
  async blockUser(uid: string, disabled: boolean): Promise<void> {
    return this.http.put<void>(
      `${this.apiUrl}/${uid}/block`,
      { disabled },
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  // ==================== ELIMINAR USUARIO ====================

  /**
   * Elimina un usuario permanentemente (solo admin)
   */
  async deleteUser(uid: string): Promise<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/${uid}`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }

  // ==================== ESTADO BIOMÉTRICO ====================

  /**
   * Obtiene el estado biométrico de todos los usuarios (solo admin)
   */
  async getBiometricStatusAll(): Promise<BiometricStatus[]> {
    return this.http.get<BiometricStatus[]>(
      `${this.apiUrl}/biometric-status`,
      { headers: this.getAuthHeaders() }
    ).toPromise();
  }
}
```

### **Página de Administración de Usuarios**

```typescript
// pages/admin/users/users.page.ts
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { 
  AlertController, 
  LoadingController, 
  ToastController 
} from '@ionic/angular';
import { AdminService, UserProfile } from '../../../services/admin.service';

@Component({
  selector: 'app-admin-users',
  templateUrl: './users.page.html'
})
export class AdminUsersPage implements OnInit {
  users: UserProfile[] = [];
  filteredUsers: UserProfile[] = [];
  searchTerm: string = '';

  constructor(
    private adminService: AdminService,
    private router: Router,
    private alertController: AlertController,
    private loadingController: LoadingController,
    private toastController: ToastController
  ) {}

  async ngOnInit() {
    await this.loadUsers();
  }

  async loadUsers() {
    const loading = await this.loadingController.create({
      message: 'Cargando usuarios...'
    });
    await loading.present();

    try {
      this.users = await this.adminService.getAllUsers();
      this.filteredUsers = [...this.users];
      await loading.dismiss();
    } catch (error) {
      await loading.dismiss();
      this.showToast('Error al cargar usuarios', 'danger');
    }
  }

  filterUsers() {
    const term = this.searchTerm.toLowerCase();
    this.filteredUsers = this.users.filter(user =>
      user.email.toLowerCase().includes(term) ||
      user.username.toLowerCase().includes(term) ||
      user.name.toLowerCase().includes(term) ||
      user.lastname.toLowerCase().includes(term)
    );
  }

  async viewUserDetails(uid: string) {
    this.router.navigate(['/admin/users', uid]);
  }

  async blockUser(user: UserProfile) {
    const alert = await this.alertController.create({
      header: 'Confirmar',
      message: `¿Estás seguro de ${user.disabled ? 'desbloquear' : 'bloquear'} a ${user.email}?`,
      buttons: [
        {
          text: 'Cancelar',
          role: 'cancel'
        },
        {
          text: 'Confirmar',
          handler: async () => {
            await this.confirmBlock(user.uid, !user.disabled);
          }
        }
      ]
    });
    await alert.present();
  }

  private async confirmBlock(uid: string, disabled: boolean) {
    const loading = await this.loadingController.create({
      message: disabled ? 'Bloqueando usuario...' : 'Desbloqueando usuario...'
    });
    await loading.present();

    try {
      await this.adminService.blockUser(uid, disabled);
      await loading.dismiss();
      this.showToast(
        disabled ? 'Usuario bloqueado' : 'Usuario desbloqueado',
        'success'
      );
      await this.loadUsers();
    } catch (error) {
      await loading.dismiss();
      this.showToast('Error al modificar usuario', 'danger');
    }
  }

  async deleteUser(user: UserProfile) {
    const alert = await this.alertController.create({
      header: '⚠️ Eliminar Usuario',
      message: `¿Estás COMPLETAMENTE SEGURO de eliminar a ${user.email}? Esta acción NO se puede deshacer.`,
      inputs: [
        {
          name: 'confirmation',
          type: 'text',
          placeholder: 'Escribe "ELIMINAR" para confirmar'
        }
      ],
      buttons: [
        {
          text: 'Cancelar',
          role: 'cancel'
        },
        {
          text: 'Eliminar',
          role: 'destructive',
          handler: async (data) => {
            if (data.confirmation === 'ELIMINAR') {
              await this.confirmDelete(user.uid);
            } else {
              this.showToast('Confirmación incorrecta', 'warning');
              return false;
            }
          }
        }
      ]
    });
    await alert.present();
  }

  private async confirmDelete(uid: string) {
    const loading = await this.loadingController.create({
      message: 'Eliminando usuario...'
    });
    await loading.present();

    try {
      await this.adminService.deleteUser(uid);
      await loading.dismiss();
      this.showToast('Usuario eliminado correctamente', 'success');
      await this.loadUsers();
    } catch (error) {
      await loading.dismiss();
      this.showToast('Error al eliminar usuario', 'danger');
    }
  }

  async updateCredentials(user: UserProfile) {
    const alert = await this.alertController.create({
      header: 'Actualizar Credenciales',
      subHeader: user.email,
      inputs: [
        {
          name: 'newEmail',
          type: 'email',
          placeholder: 'Nuevo email (opcional)',
          value: ''
        },
        {
          name: 'newPassword',
          type: 'password',
          placeholder: 'Nueva contraseña (opcional)',
          value: ''
        }
      ],
      buttons: [
        {
          text: 'Cancelar',
          role: 'cancel'
        },
        {
          text: 'Actualizar',
          handler: async (data) => {
            if (!data.newEmail && !data.newPassword) {
              this.showToast('Debes ingresar al menos un campo', 'warning');
              return false;
            }
            await this.confirmUpdateCredentials(user.uid, data);
          }
        }
      ]
    });
    await alert.present();
  }

  private async confirmUpdateCredentials(uid: string, data: any) {
    const loading = await this.loadingController.create({
      message: 'Actualizando credenciales...'
    });
    await loading.present();

    try {
      const credentials: any = {};
      if (data.newEmail) credentials.newEmail = data.newEmail;
      if (data.newPassword) credentials.newPassword = data.newPassword;

      await this.adminService.updateUserCredentials(uid, credentials);
      await loading.dismiss();
      this.showToast('Credenciales actualizadas', 'success');
      await this.loadUsers();
    } catch (error) {
      await loading.dismiss();
      this.showToast('Error al actualizar credenciales', 'danger');
    }
  }

  private async showToast(message: string, color: string) {
    const toast = await this.toastController.create({
      message,
      duration: 3000,
      color,
      position: 'bottom'
    });
    await toast.present();
  }
}
```

### **Template HTML Admin**

```html
<!-- pages/admin/users/users.page.html -->
<ion-header>
  <ion-toolbar>
    <ion-buttons slot="start">
      <ion-back-button defaultHref="/admin"></ion-back-button>
    </ion-buttons>
    <ion-title>Administrar Usuarios</ion-title>
    <ion-buttons slot="end">
      <ion-button (click)="loadUsers()">
        <ion-icon name="refresh"></ion-icon>
      </ion-button>
    </ion-buttons>
  </ion-toolbar>
  
  <ion-toolbar>
    <ion-searchbar 
      [(ngModel)]="searchTerm" 
      (ionInput)="filterUsers()"
      placeholder="Buscar usuarios...">
    </ion-searchbar>
  </ion-toolbar>
</ion-header>

<ion-content>
  <ion-list>
    <ion-item *ngFor="let user of filteredUsers">
      <ion-avatar slot="start">
        <ion-icon name="person-circle" size="large"></ion-icon>
      </ion-avatar>
      
      <ion-label>
        <h2>{{ user.name }} {{ user.lastname }}</h2>
        <p>{{ user.email }}</p>
        <p>
          <ion-badge color="primary">{{ user.role }}</ion-badge>
          <ion-badge *ngIf="user.totpEnabled" color="success">TOTP</ion-badge>
          <ion-badge *ngIf="user.biometricEnabled" color="tertiary">Bio</ion-badge>
        </p>
      </ion-label>

      <ion-button 
        slot="end" 
        fill="clear" 
        (click)="viewUserDetails(user.uid)">
        <ion-icon name="eye"></ion-icon>
      </ion-button>

      <ion-button 
        slot="end" 
        fill="clear" 
        (click)="updateCredentials(user)">
        <ion-icon name="create"></ion-icon>
      </ion-button>

      <ion-button 
        slot="end" 
        fill="clear" 
        [color]="user.disabled ? 'success' : 'warning'"
        (click)="blockUser(user)">
        <ion-icon [name]="user.disabled ? 'lock-open' : 'lock-closed'"></ion-icon>
      </ion-button>

      <ion-button 
        slot="end" 
        fill="clear" 
        color="danger"
        (click)="deleteUser(user)">
        <ion-icon name="trash"></ion-icon>
      </ion-button>
    </ion-item>
  </ion-list>

  <ion-fab vertical="bottom" horizontal="end" slot="fixed">
    <ion-fab-button>
      <ion-icon name="add"></ion-icon>
    </ion-fab-button>
  </ion-fab>
</ion-content>
```

---

## 🔐 Guard para Proteger Rutas de Admin

```typescript
// guards/admin.guard.ts
import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {
  
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(): boolean {
    const user = this.authService.getCurrentUser();
    
    if (user && user.role === 'ADMIN') {
      return true;
    }

    this.router.navigate(['/home']);
    return false;
  }
}
```

### **Configurar Rutas**

```typescript
// app-routing.module.ts
const routes: Routes = [
  // ... rutas existentes
  {
    path: 'admin',
    children: [
      {
        path: 'users',
        loadChildren: () => import('./pages/admin/users/users.module').then(m => m.UsersPageModule),
        canActivate: [AdminGuard]
      },
      {
        path: 'users/:uid',
        loadChildren: () => import('./pages/admin/user-detail/user-detail.module').then(m => m.UserDetailPageModule),
        canActivate: [AdminGuard]
      }
    ]
  }
];
```

---

## 📊 Códigos de Estado HTTP

| Código | Significado | Cuándo Ocurre |
|--------|-------------|---------------|
| 200 | OK | Operación exitosa |
| 400 | Bad Request | Datos inválidos (ej: código TOTP incorrecto) |
| 401 | Unauthorized | Token inválido o expirado |
| 403 | Forbidden | No tienes permisos (no eres admin) |
| 404 | Not Found | Usuario no encontrado |
| 500 | Internal Server Error | Error en el servidor |

---

## 🎯 Resumen de Permisos

| Endpoint | Público | Usuario | Admin |
|----------|---------|---------|-------|
| `/register` | ✅ | ✅ | ✅ |
| `/login` | ✅ | ✅ | ✅ |
| `/login/biometric` | ✅ | ✅ | ✅ |
| `/login/totp` | ✅ | ✅ | ✅ |
| `/totp/setup` | ❌ | ✅ | ✅ |
| `/totp/verify` | ❌ | ✅ | ✅ |
| `/totp/disable` | ❌ | ✅ | ✅ |
| `/totp/status` | ❌ | ✅ | ✅ |
| `/biometric` | ❌ | ✅ | ✅ |
| `GET /users` | ❌ | ❌ | ✅ |
| `GET /users/{uid}` | ❌ | ❌ | ✅ |
| `PUT /users/{uid}/credentials` | ❌ | ❌ | ✅ |
| `PUT /users/{uid}/block` | ❌ | ❌ | ✅ |
| `DELETE /users/{uid}` | ❌ | ❌ | ✅ |
| `GET /biometric-status` | ❌ | ❌ | ✅ |

---

## ✨ Características Implementadas

- ✅ **Autenticación completa** (usuario/contraseña y biometría)
- ✅ **TOTP (2FA)** opcional para todos los usuarios
- ✅ **Gestión de usuarios** completa para admins
- ✅ **Bloqueo/desbloqueo** de cuentas
- ✅ **Actualización de credenciales** por admins
- ✅ **Eliminación de usuarios**
- ✅ **Auditoría** de todas las acciones
- ✅ **CORS configurado** para desarrollo

---

## 🚀 Próximos Pasos

1. **Copia el servicio `AdminService`** a tu proyecto Ionic
2. **Crea las páginas de administración** con los componentes proporcionados
3. **Configura el Guard** para proteger rutas de admin
4. **Actualiza la URL del API** en los servicios
5. **Prueba todos los endpoints** con un usuario admin

---

**¿Necesitas ayuda con alguna implementación específica del panel de administración?**

