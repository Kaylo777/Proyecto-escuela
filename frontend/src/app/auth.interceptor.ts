import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './services/auth.service';

/**
 * Envia el access token en cada peticion y lo renueva solo.
 *
 * <p>El access token dura 15 min. Cuando caduca (o el backend responde 401) se
 * pide un token nuevo con el refresh token y se reintenta la peticion una sola
 * vez. Si el refresh tambien falla, se cierra la sesion y se vuelve al login.</p>
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Solo /auth/login y /auth/refresh viajan sin token: mandarlos con uno caducado
  // provocaria un 401 infinito. /auth/me SI lleva token, porque el gateway lo exige.
  const RUTAS_SIN_TOKEN = ['/auth/login', '/auth/refresh'];
  const esPeticionDeAuth = RUTAS_SIN_TOKEN.some((ruta) => req.url.includes(ruta));
  const conToken = (peticion: typeof req, token: string) =>
    peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  const renovar = (peticion: typeof req) => {
    const refreshToken = auth.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new HttpErrorResponse({ status: 401 }));
    }
    return auth.refresh().pipe(
      switchMap((respuesta) => {
        auth.guardarSesion(respuesta);
        return next(conToken(peticion, respuesta.accessToken));
      }),
      catchError((error) => {
        cerrarSesion();
        return throwError(() => error);
      }),
    );
  };

  const cerrarSesion = () => {
    auth.cerrarSesion();
    router.navigate(['/login']);
  };

  if (esPeticionDeAuth) {
    return next(req);
  }

  const token = auth.getToken();
  if (!token) {
    return next(req);
  }

  // Renovacion proactiva: si el token ya caduco, se cambia antes de enviar.
  const peticion = auth.tokenExpirado() ? null : conToken(req, token);
  if (peticion) {
    return next(peticion).pipe(
      catchError((error) => (error.status === 401 ? renovar(req) : throwError(() => error))),
    );
  }

  return renovar(req);
};
