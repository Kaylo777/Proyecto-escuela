package com.example.auth_service.dto;

/**
 * Credenciales que llegan a POST /auth/login.
 *
 * <p>Es un record y no la entidad de usuario a proposito: el controller nunca
 * toca el modelo de dominio, solo DTOs. Asi el JSON de entrada queda definido por
 * la API y no por la base de datos.</p>
 */
public record LoginRequest(
        String username,
        String password
) {
}