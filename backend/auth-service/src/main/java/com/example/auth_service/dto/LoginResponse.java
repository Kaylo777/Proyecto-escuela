package com.example.auth_service.dto;

/**
 * Respuesta de login y de refresh.
 *
 * <p>El access token es el que viaja en Authorization: Bearer. El refresh token
 * se devuelve aparte para que el cliente lo guarde en un almacen distinto del
 * access (idealmente uno accesible a JavaScript).</p>
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String username,
        java.util.List<String> roles
) {
}