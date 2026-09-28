import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
  imports: [FormsModule],
})
export class LoginComponent {
  usuario = '';
  password = '';
  error = signal('');
  cargando = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  entrar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.auth.login(this.usuario, this.password).subscribe({
      next: (resp) => {
        this.auth.guardarSesion(resp);
        this.router.navigate(['/alumnos']);
      },
      error: () => {
        this.cargando.set(false);
        this.error.set('Usuario o contraseña incorrectos.');
      },
    });
  }
}