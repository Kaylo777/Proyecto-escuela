import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  constructor(
    public auth: AuthService,
    private router: Router,
  ) {}

  salir(): void {
    this.auth.cerrarSesion();
    this.router.navigate(['/login']);
  }
}
