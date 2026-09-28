import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Alumno } from '../models/alumno';
import { AlumnoService } from '../services/alumno.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-alumnos',
  templateUrl: './alumnos.html',
  styleUrl: './alumnos.css',
  imports: [FormsModule],
})
export class AlumnosComponent implements OnInit {
  alumnos = signal<Alumno[]>([]);
  mensaje = signal('');
  editando = signal<Alumno | null>(null);
  formulario: Alumno = this.nuevoFormulario();

  constructor(
    private alumnoService: AlumnoService,
    public auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  /** USER solo lee; ADMIN tambien crea, edita y elimina. */
  get esAdmin(): boolean {
    return this.auth.esAdmin();
  }

  cargar(): void {
    this.alumnoService.listar().subscribe({
      next: (data) => this.alumnos.set(data),
      error: (error: HttpErrorResponse) => this.mensaje.set(this.explicarError(error)),
    });
  }

  private explicarError(error: HttpErrorResponse): string {
    if (error.status === 401) {
      return 'Sesión vencida o token inválido. Vuelva a iniciar sesión.';
    }
    if (error.status === 403) {
      return 'Su rol no tiene permisos para esta operación (se requiere ADMIN).';
    }
    return 'Error al obtener los alumnos. Verifique que el gateway esté activo.';
  }

  nuevoFormulario(): Alumno {
    return { id: 0, nombre: '', apellido: '', dni: '', email: '', curso: '', fechaNacimiento: '' };
  }

  editar(alumno: Alumno): void {
    this.editando.set(alumno);
    this.formulario = { ...alumno };
  }

  cancelar(): void {
    this.editando.set(null);
    this.formulario = this.nuevoFormulario();
  }

  guardar(): void {
    if (this.editando()) {
      const original = this.editando()!;
      this.alumnoService.actualizar(original.id, this.formulario).subscribe({
        next: () => {
          this.mensaje.set('Alumno actualizado correctamente. Evento de alumno emitido a RabbitMQ.');
          this.cancelar();
          this.cargar();
        },
        error: () => this.mensaje.set('Error al actualizar el alumno.'),
      });
    } else {
      this.alumnoService.crear(this.formulario).subscribe({
        next: () => {
          this.mensaje.set('Alumno creado correctamente. Evento de alumno emitido a RabbitMQ.');
          this.cancelar();
          this.cargar();
        },
        error: () => this.mensaje.set('Error al crear el alumno.'),
      });
    }
  }

  eliminar(alumno: Alumno): void {
    this.alumnoService.eliminar(alumno.id).subscribe({
      next: () => {
        this.mensaje.set('Alumno eliminado.');
        this.cargar();
      },
      error: () => this.mensaje.set('Error al eliminar el alumno.'),
    });
  }
}