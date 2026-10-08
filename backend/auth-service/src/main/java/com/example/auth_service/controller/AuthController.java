package com.example.auth_service.controller;

import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RefreshRequest;
import com.example.auth_service.dto.UserProfileResponse;
import com.example.auth_service.security.JwtService;
import com.example.auth_service.service.UserDetailsServiceFromConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de identidad. Todo lo que el gateway antes hacia de autenticacion
 * ahora vive aqui: login, refresh y perfil del usuario vigente.
 *
 * <p>El controller solo orquesta DTOs. No toca PasswordEncoder ni JwtService:
 * por eso la logica de negocio esta en AuthService y aca queda la capa HTTP.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsServiceFromConfig usuarios;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserDetailsServiceFromConfig usuarios) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    /** Valida credenciales y emite access + refresh token. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));

            String username = auth.getName();
            List<String> roles = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .toList();

            return ResponseEntity.ok(new LoginResponse(
                    jwtService.generarAccessToken(username, roles),
                    jwtService.generarRefreshToken(username),
                    "Bearer",
                    jwtService.getExpirationSegundos(),
                    username,
                    roles));
        } catch (org.springframework.security.core.AuthenticationException e) {
            // 401 generico a proposito: no revela si el usuario existe o si la
            // contrasena esta mal, para no facilitar un ataque de fuerza bruta.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null, null));
        }
    }

    /**
     * Renueva el access token. Acepta el refresh token en el CUERPO, no como
     * Bearer: si se aceptara en la cabecera, el filtro de resource server lo
     * rechazaria antes de llegar aqui, porque el refresh se firma con otro
     * secreto.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        String token = request.refreshToken();

        if (token == null || !jwtService.esRefreshTokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null, null));
        }

        String username = jwtService.extraerUsuarioDeRefresh(token);

        // El token puede ser valido y aun asi el usuario ya no existir.
        if (!usuarios.existe(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, null, 0, null, null));
        }

        List<String> roles = usuarios.rolesDe(username);
        return ResponseEntity.ok(new LoginResponse(
                jwtService.generarAccessToken(username, roles),
                null,
                "Bearer",
                jwtService.getExpirationSegundos(),
                username,
                roles));
    }

    /** Perfil del usuario del token vigente. Sirve para que el front restaure la sesion. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new InvalidBearerTokenException("Token ausente");
        }
        Object roles = jwt.getClaims().get("roles");
        List<String> lista = roles instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();
        return ResponseEntity.ok(new UserProfileResponse(jwt.getSubject(), lista));
    }
}