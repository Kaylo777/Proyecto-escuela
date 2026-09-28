import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

/** Exige sesion iniciada; si no, vuelve al login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

/**
 * Exige un rol concreto (ej. 'ADMIN'). El backend tambien lo valida: esto solo
 * evita mostrar pantallas a quien no puede usarlas, no reemplaza al servidor.
 */
export const roleGuard = (rol: string): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return auth.tieneRol(rol) ? true : router.createUrlTree(['/alumnos']);
  };
};
