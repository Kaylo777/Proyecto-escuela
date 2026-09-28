import { Component, OnInit, signal } from '@angular/core';
import { AuthService } from '../services/auth.service';

interface Claim {
  nombre: string;
  valor: string;
}

/**
 * Panel de seguridad (solo ADMIN). Muestra el token JWT decodificado y permite
 * comprobar contra el gateway que el token sigue siendo valido.
 *
 * <p>Sirve para la defensa del trabajo: se ve de forma explicita que el token
 * viaja firmado, cual es su emisor, que caduca y que permisos trae.</p>
 */
@Component({
  selector: 'app-seguridad',
  templateUrl: './seguridad.html',
})
export class SeguridadComponent implements OnInit {
  claims = signal<Claim[]>([]);
  verificacion = signal('');

  constructor(public auth: AuthService) {}

  ngOnInit(): void {
    this.claims.set(this.decodificarToken());
  }

  /** El payload del JWT es Base64URL; solo se lee, la firma no se valida aqui. */
  private decodificarToken(): Claim[] {
    const token = this.auth.getToken();
    if (!token) {
      return [];
    }
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      const datos = JSON.parse(json);
      return Object.entries(datos).map(([nombre, valor]) => ({
        nombre,
        valor: typeof valor === 'object' ? JSON.stringify(valor) : String(valor),
      }));
    } catch {
      return [];
    }
  }

  /** Renueva el access token con el refresh token y muestra el token nuevo. */
  renovar(): void {
    this.auth.refresh().subscribe({
      next: (respuesta) => {
        this.auth.guardarSesion(respuesta);
        this.claims.set(this.decodificarToken());
        this.verificacion.set('Token renovado correctamente.');
      },
      error: () => this.verificacion.set('No se pudo renovar el token. Inicie sesión otra vez.'),
    });
  }

  /** Consulta GET /auth/me: el gateway responde si el token es válido. */
  verificar(): void {
    this.auth.me().subscribe({
      next: (respuesta) =>
        this.verificacion.set(
          `El gateway validó el token. Usuario: ${respuesta.username} · Roles: ${respuesta.roles.join(', ')}`,
        ),
      error: () => this.verificacion.set('El gateway rechazó el token (401).'),
    });
  }
}
