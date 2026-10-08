package com.example.auth_service.dto;

import java.util.List;

/** Respuesta de GET /auth/me: quien es el usuario del token vigente. */
public record UserProfileResponse(
        String username,
        List<String> roles
) {
}