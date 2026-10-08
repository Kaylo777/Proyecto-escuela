package com.example.alumnos.dto;

import java.time.LocalDate;

/**
 * Respuesta de la API de alumnos.
 *
 * <p>Es un espejo de la entidad, pero con su propia forma: si manana la tabla suma
 * una columna interna (por ejemplo un campo de auditoria), se agrega aca y la
 * entidad ni se entera.</p>
 */
public record AlumnoResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        String email,
        String curso,
        LocalDate fechaNacimiento
) {
}