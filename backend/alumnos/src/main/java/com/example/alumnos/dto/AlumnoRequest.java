package com.example.alumnos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos de entrada para crear o actualizar un alumno.
 *
 * <p><b>Por que existe:</b> el controller no recibe la entidad {@code Alumno}.
 * Si lo hiciera, un cliente podria mandar campos que no le corresponden, como el
 * {@code id} para sobrescribir el registro de otro, y el JSON de entrada quedaria
 * atado al esquema de la base de datos.</p>
 *
 * <p>Las anotaciones de Bean Validation dejan la validacion al framework, que
 * devuelve un 400 con el detalle, en vez de una SQLException del contenedor.</p>
 */
public record AlumnoRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar los 80 caracteres")
        String apellido,

        @Pattern(regexp = "^[0-9]{7,10}$", message = "El DNI debe tener entre 7 y 10 digitos")
        String dni,

        @Email(message = "El email no tiene un formato valido")
        @Size(max = 120, message = "El email no puede superar los 120 caracteres")
        String email,

        @Size(max = 20, message = "El curso no puede superar los 20 caracteres")
        String curso,

        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        LocalDate fechaNacimiento
) {
}