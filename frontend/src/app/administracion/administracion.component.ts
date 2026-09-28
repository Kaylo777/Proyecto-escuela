import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Docente } from '../models/docente';
import { DocenteService } from '../services/docente.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-administracion',
  templateUrl: './administracion.html',
  styleUrl: './administracion.css',
  imports: [FormsModule],
})
export class AdministracionComponent implements OnInit {
  docentes = signal<Docente[]>([]);
  mensaje = signal('');
  editando = signal<Docente | null>(null);
  formulario: Docente = this.nuevoFormulario();

  constructor(
    private docenteService: DocenteService,
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
    this.docenteService.listar().subscribe({
      next: (data) => this.docentes.set(data),
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
    return 'Error al obtener los docentes. Verifique que el gateway esté activo.';
  }

  nuevoFormulario(): Docente {
    return { id: 0, nombre: '', apellido: '', email: '', especialidad: '' };
  }

  editar(docente: Docente): void {
    this.editando.set(docente);
    this.formulario = { ...docente };
  }

  cancelar(): void {
    this.editando.set(null);
    this.formulario = this.nuevoFormulario();
  }

  guardar(): void {
    if (this.editando()) {
      const original = this.editando()!;
      this.docenteService.actualizar(original.id, this.formulario).subscribe({
        next: () => {
          this.mensaje.set('Docente actualizado correctamente.');
          this.cancelar();
          this.cargar();
        },
        error: () => this.mensaje.set('Error al actualizar el docente.'),
      });
    } else {
      this.docenteService.crear(this.formulario).subscribe({
        next: () => {
          this.mensaje.set('Docente creado correctamente. Evento de docente emitido a RabbitMQ.');
          this.cancelar();
          this.cargar();
        },
        error: () => this.mensaje.set('Error al crear el docente.'),
      });
    }
  }

  eliminar(docente: Docente): void {
    this.docenteService.eliminar(docente.id).subscribe({
      next: () => {
        this.mensaje.set('Docente eliminado.');
        this.cargar();
      },
      error: () => this.mensaje.set('Error al eliminar el docente.'),
    });
  }
}