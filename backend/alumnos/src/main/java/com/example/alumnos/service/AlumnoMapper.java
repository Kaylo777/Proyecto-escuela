package com.example.alumnos.service;

import com.example.alumnos.dto.AlumnoRequest;
import com.example.alumnos.dto.AlumnoResponse;
import com.example.alumnos.model.Alumno;

/**
 * Convierte entre la entidad y los DTOs. Es el unico lugar donde se conoce la
 * forma de ambas: los controllers no tocan campos de la entidad directamente.
 */
public final class AlumnoMapper {

    private AlumnoMapper() {
    }

    public static AlumnoResponse toResponse(Alumno alumno) {
        return new AlumnoResponse(
                alumno.getId(),
                alumno.getNombre(),
                alumno.getApellido(),
                alumno.getDni(),
                alumno.getEmail(),
                alumno.getCurso(),
                alumno.getFechaNacimiento());
    }

    public static java.util.List<AlumnoResponse> toResponseList(java.util.List<Alumno> alumnos) {
        return alumnos.stream().map(AlumnoMapper::toResponse).toList();
    }

    /** Entidad nueva. El id lo asigna la base de datos, nunca el cliente. */
    public static Alumno toEntity(AlumnoRequest request) {
        Alumno alumno = new Alumno();
        alumno.setNombre(request.nombre());
        alumno.setApellido(request.apellido());
        alumno.setDni(request.dni());
        alumno.setEmail(request.email());
        alumno.setCurso(request.curso());
        alumno.setFechaNacimiento(request.fechaNacimiento());
        return alumno;
    }

    /**
     * Actualizacion parcial sobre una entidad existente. Se ignoran los campos
     * nulos para que un PUT parcial no borre datos: para dejar un campo vacio hay
     * que mandarlo vacio, no omitirlo.
     */
    public static void actualizar(Alumno alumno, AlumnoRequest request) {
        if (request.nombre() != null) {
            alumno.setNombre(request.nombre());
        }
        if (request.apellido() != null) {
            alumno.setApellido(request.apellido());
        }
        if (request.dni() != null) {
            alumno.setDni(request.dni());
        }
        if (request.email() != null) {
            alumno.setEmail(request.email());
        }
        if (request.curso() != null) {
            alumno.setCurso(request.curso());
        }
        if (request.fechaNacimiento() != null) {
            alumno.setFechaNacimiento(request.fechaNacimiento());
        }
    }
}