package com.example.administracion.service;

import com.example.administracion.dto.DocenteRequest;
import com.example.administracion.dto.DocenteResponse;
import com.example.administracion.model.Docente;

/**
 * Convierte entre la entidad y los DTOs. Es el unico lugar donde se conoce la
 * forma de ambas.
 */
public final class DocenteMapper {

    private DocenteMapper() {
    }

    public static DocenteResponse toResponse(Docente docente) {
        return new DocenteResponse(
                docente.getId(),
                docente.getNombre(),
                docente.getApellido(),
                docente.getEmail(),
                docente.getEspecialidad());
    }

    public static java.util.List<DocenteResponse> toResponseList(java.util.List<Docente> docentes) {
        return docentes.stream().map(DocenteMapper::toResponse).toList();
    }

    /** Entidad nueva. El id lo asigna la base de datos, nunca el cliente. */
    public static Docente toEntity(DocenteRequest request) {
        Docente docente = new Docente();
        docente.setNombre(request.nombre());
        docente.setApellido(request.apellido());
        docente.setEmail(request.email());
        docente.setEspecialidad(request.especialidad());
        return docente;
    }

    /**
     * Actualizacion parcial: los campos nulos se ignoran, asi un PUT parcial no
     * borra datos. Para dejar un campo vacio hay que mandarlo vacio, no omitirlo.
     */
    public static void actualizar(Docente docente, DocenteRequest request) {
        if (request.nombre() != null) {
            docente.setNombre(request.nombre());
        }
        if (request.apellido() != null) {
            docente.setApellido(request.apellido());
        }
        if (request.email() != null) {
            docente.setEmail(request.email());
        }
        if (request.especialidad() != null) {
            docente.setEspecialidad(request.especialidad());
        }
    }
}