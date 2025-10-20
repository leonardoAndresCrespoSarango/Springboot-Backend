# Integración con Angular - JWT Authentication

## 📋 Resumen

Tu backend ahora usa **JWT (JSON Web Tokens)** para autenticación. El flujo es:

1. Usuario hace **login** → Backend retorna `{token, user}`
2. Frontend guarda el **token** (en localStorage o sessionStorage)
3. Frontend incluye el token en **cada petición** con header `Authorization: Bearer {token}`
4. Backend valida el token y permite/deniega acceso

---

## 🚨 IMPORTANTE: Evitar Loop Infinito en Interceptor

**PROBLEMA:** Si el interceptor llama a `logout()` en cada error 401/403, puede crear un loop infinito de peticiones.

**SOLUCIÓN:** Solo hacer logout en errores 401/403 de endpoints **protegidos**, NO en endpoints públicos.

```typescript
// src/app/interceptors/auth.interceptor.ts
import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  // URLs públicas que NO deben causar logout
  private publicUrls = [
    '/users/login',
    '/users/register',
    '/users/password-reset',
    '/users/audit/logout',
    '/users/audit/login-failed'
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Obtener el token
    const token = this.authService.getToken();

    // Si hay token, agregarlo al header Authorization
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    // Manejar errores de autenticación
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 || error.status === 403) {
          // Solo hacer logout si NO es una URL pública
          const isPublicUrl = this.publicUrls.some(url => request.url.includes(url));
          
          if (!isPublicUrl) {
            console.warn('Token inválido o expirado, cerrando sesión...');
            this.authService.logout();
            this.router.navigate(['/login']);
          }
        }
        return throwError(() => error);
      })
    );
  }
}
```

---

## 🚀 Implementación en Angular

### 1. Crear el Modelo de Usuario

```typescript
// src/app/models/user.model.ts
export interface User {
  uid: string;
  username: string;
  email: string;
  name: string;
  lastname: string;
  role: 'CUSTOMER' | 'ADMIN';
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  name: string;
  lastname: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}
```

---

### 2. Crear el Servicio de Autenticación

```typescript
// src/app/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8002/users';
  private readonly TOKEN_KEY = 'jwt_token';
  private readonly USER_KEY = 'current_user';

  // Observable para saber si el usuario está autenticado
  private currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  // Observable para saber si está logueado
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  // Flag para evitar múltiples llamadas de logout
  private isLoggingOut = false;

  constructor(private http: HttpClient) {}

  /**
   * Registrar nuevo usuario
   */
  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.API_URL}/register`, request);
  }

  /**
   * Login
   */
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, request).pipe(
      tap(response => {
        // Guardar token y usuario
        this.setToken(response.token);
        this.setUser(response.user);
        
        // Notificar a los observadores
        this.currentUserSubject.next(response.user);
        this.isAuthenticatedSubject.next(true);
        this.isLoggingOut = false;
      })
    );
  }

  /**
   * Logout
   */
  logout(): void {
    // Evitar múltiples llamadas simultáneas
    if (this.isLoggingOut) {
      return;
    }
    
    this.isLoggingOut = true;

    // Opcional: llamar al backend para auditoría (sin esperar respuesta)
    if (this.hasToken()) {
      this.http.post(`${this.API_URL}/audit/logout`, {}).subscribe({
        error: (err) => console.warn('Error logging out:', err)
      });
    }

    // Limpiar storage inmediatamente
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    
    // Notificar a los observadores
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
  }

  /**
   * Obtener token actual
   */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Verificar si tiene token
   */
  hasToken(): boolean {
    return this.getToken() !== null;
  }

  /**
   * Obtener usuario actual
   */
  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Verificar si el usuario es ADMIN
   */
  isAdmin(): boolean {
    const user = this.getCurrentUser();
    return user?.role === 'ADMIN';
  }

  /**
   * Verificar si está autenticado
   */
  isAuthenticated(): boolean {
    return this.hasToken();
  }

  // Métodos privados
  private setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  private setUser(user: User): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private getUserFromStorage(): User | null {
    const userJson = localStorage.getItem(this.USER_KEY);
    return userJson ? JSON.parse(userJson) : null;
  }
}
```

---

### 3. Crear el Interceptor HTTP (Importante)

El interceptor agrega automáticamente el token JWT a todas las peticiones HTTP:

```typescript
// src/app/interceptors/auth.interceptor.ts
import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  // URLs públicas que NO deben causar logout
  private publicUrls = [
    '/users/login',
    '/users/register',
    '/users/password-reset',
    '/users/audit/logout',
    '/users/audit/login-failed'
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Obtener el token
    const token = this.authService.getToken();

    // Si hay token, agregarlo al header Authorization
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    // Manejar errores de autenticación
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 || error.status === 403) {
          // Solo hacer logout si NO es una URL pública
          const isPublicUrl = this.publicUrls.some(url => request.url.includes(url));
          
          if (!isPublicUrl) {
            console.warn('Token inválido o expirado, cerrando sesión...');
            this.authService.logout();
            this.router.navigate(['/login']);
          }
        }
        return throwError(() => error);
      })
    );
  }
}
```

**Registrar el Interceptor en `app.config.ts` o `app.module.ts`:**

```typescript
// src/app/app.config.ts (Standalone)
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './interceptors/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor]) // Registrar interceptor
    )
  ]
};
```

O si usas módulos:

```typescript
// src/app/app.module.ts (Modules)
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './interceptors/auth.interceptor';

@NgModule({
  // ...
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ]
})
export class AppModule { }
```

---

### 4. Crear Guard para Rutas Protegidas

```typescript
// src/app/guards/auth.guard.ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Redirigir a login si no está autenticado
  router.navigate(['/login']);
  return false;
};

// Guard para solo ADMIN
export const adminGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAdmin()) {
    return true;
  }

  // Redirigir si no es ADMIN
  router.navigate(['/']);
  return false;
};
```

**Usar en las rutas:**

```typescript
// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  
  // Rutas protegidas (requieren login)
  { 
    path: 'dashboard', 
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  
  // Rutas solo para ADMIN
  { 
    path: 'admin', 
    component: AdminPanelComponent,
    canActivate: [authGuard, adminGuard]
  },
  
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
];
```

---

### 5. Componente de Login

```typescript
// src/app/components/login/login.component.ts
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="login-container">
      <h2>Login</h2>
      
      <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <label>Email:</label>
          <input 
            type="email" 
            formControlName="email"
            class="form-control"
            [class.is-invalid]="email?.invalid && email?.touched"
          />
          <div *ngIf="email?.invalid && email?.touched" class="error">
            Email es requerido
          </div>
        </div>

        <div class="form-group">
          <label>Contraseña:</label>
          <input 
            type="password" 
            formControlName="password"
            class="form-control"
            [class.is-invalid]="password?.invalid && password?.touched"
          />
          <div *ngIf="password?.invalid && password?.touched" class="error">
            Contraseña es requerida
          </div>
        </div>

        <div *ngIf="errorMessage" class="alert alert-danger">
          {{ errorMessage }}
        </div>

        <button 
          type="submit" 
          class="btn btn-primary"
          [disabled]="loginForm.invalid || loading"
        >
          {{ loading ? 'Cargando...' : 'Iniciar Sesión' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    .login-container {
      max-width: 400px;
      margin: 50px auto;
      padding: 20px;
    }
    .form-group {
      margin-bottom: 15px;
    }
    .error {
      color: red;
      font-size: 12px;
    }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;
  loading = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  get email() { return this.loginForm.get('email'); }
  get password() { return this.loginForm.get('password'); }

  onSubmit() {
    if (this.loginForm.invalid) return;

    this.loading = true;
    this.errorMessage = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: (response) => {
        console.log('Login exitoso:', response);
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        console.error('Error en login:', error);
        this.errorMessage = error.error?.message || 'Credenciales inválidas';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }
}
```

---

### 6. Ejemplo de Servicio para Llamar API Protegida

```typescript
// src/app/services/user.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly API_URL = 'http://localhost:8002/users';

  constructor(private http: HttpClient) {}

  /**
   * Listar todos los usuarios (solo ADMIN)
   * El token se agrega automáticamente por el interceptor
   */
  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.API_URL);
  }

  /**
   * Obtener usuario por ID (solo ADMIN)
   */
  getUserById(uid: string): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/${uid}`);
  }

  /**
   * Bloquear/Desbloquear usuario (solo ADMIN)
   */
  toggleUserBlock(uid: string, disabled: boolean): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${uid}/block`, { disabled });
  }

  /**
   * Actualizar credenciales (solo ADMIN)
   */
  updateCredentials(uid: string, newEmail?: string, newPassword?: string): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${uid}/credentials`, {
      newEmail,
      newPassword
    });
  }
}
```

---

## 🔒 Flujo Completo

### 1. Login
```typescript
// Usuario ingresa credenciales
const loginData = { email: 'user@example.com', password: 'pass123' };

authService.login(loginData).subscribe({
  next: (response) => {
    // Token guardado automáticamente
    // response = { token: 'eyJ...', user: {...} }
    console.log('Usuario logueado:', response.user);
  }
});
```

### 2. Hacer Peticiones Autenticadas
```typescript
// El interceptor agrega automáticamente: Authorization: Bearer {token}
userService.getAllUsers().subscribe({
  next: (users) => console.log('Usuarios:', users),
  error: (err) => {
    // Si token expiró, el interceptor redirige a /login
  }
});
```

### 3. Logout
```typescript
authService.logout(); // Limpia token y redirige
```

---

## 📝 Checklist de Implementación

- [ ] Crear modelos de datos (`user.model.ts`)
- [ ] Crear `AuthService`
- [ ] Crear `AuthInterceptor` y registrarlo
- [ ] Crear Guards (`authGuard`, `adminGuard`)
- [ ] Implementar componente de Login
- [ ] Implementar componente de Register
- [ ] Proteger rutas con Guards
- [ ] Probar flujo completo de login/logout
- [ ] Manejar expiración de token
- [ ] (Opcional) Agregar refresh token

---

## 🚨 Consideraciones Importantes

### 1. **Seguridad del Token**
- El token se guarda en `localStorage` (persiste al cerrar navegador)
- Alternativa: `sessionStorage` (se borra al cerrar pestaña)
- **NO guardar datos sensibles en el token** (solo metadatos)

### 2. **Expiración del Token**
- Tu backend tiene expiración de 24 horas por defecto
- Puedes decodificar el token en Angular para saber cuándo expira:

```typescript
import { jwtDecode } from 'jwt-decode';

isTokenExpired(): boolean {
  const token = this.getToken();
  if (!token) return true;
  
  try {
    const decoded: any = jwtDecode(token);
    const expirationDate = new Date(decoded.exp * 1000);
    return expirationDate < new Date();
  } catch {
    return true;
  }
}
```

### 3. **CORS**
Asegúrate de que tu backend permita peticiones desde Angular:

```java
// En Spring Boot, agregar configuración CORS
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

---

## 🎯 Resumen

Tu Angular debe:
1. ✅ **Login**: Llamar a `/users/login` y guardar el `token`
2. ✅ **Agregar token**: En cada petición como header `Authorization: Bearer {token}`
3. ✅ **Proteger rutas**: Usar Guards para verificar autenticación
4. ✅ **Logout**: Limpiar el token del storage
5. ✅ **Manejar errores**: Redirigir a login si token es inválido

¡Con esto tu Angular está completamente integrado con el backend JWT! 🚀
