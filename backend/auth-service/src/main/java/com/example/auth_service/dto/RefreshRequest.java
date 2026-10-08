package com.example.auth_service.dto;

/** Token de renovacion que llega a POST /auth/refresh. */
public record RefreshRequest(
        String refreshToken
) {
}