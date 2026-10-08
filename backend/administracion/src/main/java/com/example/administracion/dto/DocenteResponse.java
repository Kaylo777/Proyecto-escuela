package com.example.administracion.dto;

/**
 * Respuesta de la API de docentes.
 *
 * <p>Si la tabla suma una columna interna, se agrega aca y la entidad no se
 * entera: la forma de la API es independiente del esquema de la base.</p>
 */
public record DocenteResponse(
        Long id,
        String nombre,
        String apellido,
        String email,
        String especialidad
) {
}