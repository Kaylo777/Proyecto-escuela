package com.example.administracion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para crear o actualizar un docente.
 *
 * <p>El controller nunca recibe la entidad {@code Docente}: entre otras cosas, un
 * cliente podria mandar el {@code id} para sobrescribir el registro de otro. El
 * DTO define el contrato de entrada y deja la entidad contenida dentro.</p>
 */
public record DocenteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar los 80 caracteres")
        String apellido,

        @Email(message = "El email no tiene un formato valido")
        @Size(max = 120, message = "El email no puede superar los 120 caracteres")
        String email,

        @Size(max = 80, message = "La especialidad no puede superar los 80 caracteres")
        String especialidad
) {
}