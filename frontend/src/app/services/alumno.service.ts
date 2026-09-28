import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Alumno } from '../models/alumno';

export const API_URL = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class AlumnoService {
  private apiUrl = `${API_URL}/api/alumnos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Alumno[]> {
    return this.http.get<Alumno[]>(this.apiUrl);
  }

  crear(alumno: Alumno): Observable<Alumno> {
    const { id, ...body } = alumno;
    return this.http.post<Alumno>(this.apiUrl, body);
  }

  actualizar(id: number, alumno: Alumno): Observable<Alumno> {
    return this.http.put<Alumno>(`${this.apiUrl}/${id}`, alumno);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}