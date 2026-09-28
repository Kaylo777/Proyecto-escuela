import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './alumno.service';

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** Vida del access token en segundos. */
  expiresIn: number;
  username: string;
  roles: string[];
}

const ACCESS_KEY = 'auth_token';
const REFRESH_KEY = 'auth_refresh_token';
const USERNAME_KEY = 'auth_username';
const ROLES_KEY = 'auth_roles';
const EXPIRES_KEY = 'auth_expires_at';

@Injectable({ providedIn: 'root' })
export class AuthService {
  autenticado = signal<boolean>(!!localStorage.getItem(ACCESS_KEY));
  usuario = signal<string>(localStorage.getItem(USERNAME_KEY) ?? '');
  roles = signal<string[]>(this.leerRoles());

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_URL}/auth/login`, { username, password });
  }

  /** Canjea el refresh token por un access token nuevo (8 h de vida por token). */
  refresh(): Observable<LoginResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<LoginResponse>(`${API_URL}/auth/refresh`, { refreshToken });
  }

  /** El gateway responde whoami: usuario y roles que travel en el access token. */
  me(): Observable<{ username: string; roles: string[] }> {
    return this.http.get<{ username: string; roles: string[] }>(`${API_URL}/auth/me`);
  }

  guardarSesion(resp: LoginResponse): void {
    localStorage.setItem(ACCESS_KEY, resp.accessToken);
    localStorage.setItem(REFRESH_KEY, resp.refreshToken);
    localStorage.setItem(USERNAME_KEY, resp.username);
    localStorage.setItem(ROLES_KEY, JSON.stringify(resp.roles ?? []));
    localStorage.setItem(EXPIRES_KEY, String(Date.now() + resp.expiresIn * 1000));
    this.autenticado.set(true);
    this.usuario.set(resp.username);
    this.roles.set(resp.roles ?? []);
  }

  cerrarSesion(): void {
    [ACCESS_KEY, REFRESH_KEY, USERNAME_KEY, ROLES_KEY, EXPIRES_KEY].forEach((clave) =>
      localStorage.removeItem(clave),
    );
    this.autenticado.set(false);
    this.usuario.set('');
    this.roles.set([]);
  }

  getToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  isAuthenticated(): boolean {
    return !!localStorage.getItem(ACCESS_KEY);
  }

  /** El access token ya caduco y habria que renovarlo con el refresh token. */
  tokenExpirado(): boolean {
    const expiracion = localStorage.getItem(EXPIRES_KEY);
    if (!expiracion) {
      return false;
    }
    return Date.now() >= Number(expiracion);
  }

  /** El backend expone los roles sin prefijo (ADMIN, USER). */
  tieneRol(rol: string): boolean {
    return this.roles().includes(rol) || this.roles().includes(`ROLE_${rol}`);
  }

  esAdmin(): boolean {
    return this.tieneRol('ADMIN');
  }

  private leerRoles(): string[] {
    try {
      return JSON.parse(localStorage.getItem(ROLES_KEY) ?? '[]');
    } catch {
      return [];
    }
  }
}
