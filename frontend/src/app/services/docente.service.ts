import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Docente } from '../models/docente';
import { API_URL } from './alumno.service';

@Injectable({ providedIn: 'root' })
export class DocenteService {
  private apiUrl = `${API_URL}/api/administracion`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Docente[]> {
    return this.http.get<Docente[]>(this.apiUrl);
  }

  crear(docente: Docente): Observable<Docente> {
    const { id, ...body } = docente;
    return this.http.post<Docente>(this.apiUrl, body);
  }

  actualizar(id: number, docente: Docente): Observable<Docente> {
    return this.http.put<Docente>(`${this.apiUrl}/${id}`, docente);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}