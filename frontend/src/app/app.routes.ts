import { Routes } from '@angular/router';
import { AlumnosComponent } from './alumnos/alumnos.component';
import { AdministracionComponent } from './administracion/administracion.component';
import { LoginComponent } from './login/login.component';
import { SeguridadComponent } from './seguridad/seguridad.component';
import { authGuard, roleGuard } from './auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'alumnos', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, title: 'Iniciar sesión' },
  { path: 'alumnos', component: AlumnosComponent, title: 'Alumnos', canActivate: [authGuard] },
  { path: 'administracion', component: AdministracionComponent, title: 'Administración', canActivate: [authGuard] },
  // El panel de seguridad solo existe para ADMIN: el guard de rol evita mostrarlo
  // y el backend responde 403 si alguien fuerza la URL sin el rol.
  {
    path: 'seguridad',
    component: SeguridadComponent,
    title: 'Seguridad',
    canActivate: [authGuard, roleGuard('ADMIN')],
  },
  { path: '**', redirectTo: 'alumnos' },
];
