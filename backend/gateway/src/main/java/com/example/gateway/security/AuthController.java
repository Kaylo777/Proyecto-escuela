package com.example.gateway.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de autenticacion del gateway.
 *
 * <ul>
 *   <li>POST /auth/login   -> access token (15 min) + refresh token (8 h)</li>
 *   <li>POST /auth/refresh -> renueva el access token con el refresh token</li>
 *   <li>GET  /auth/me      -> usuario y roles del token presentado</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AuthController(@Qualifier("authenticationManager") ReactiveAuthenticationManager authenticationManager,
                          JwtService jwtService,
                          SecurityProperties securityProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return errorLogin();
        }
        return authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()))
                .map(this::respuestaOk)
                .onErrorResume(error -> errorLogin());
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refresh(@RequestBody RefreshRequest request) {
        if (request == null || request.refreshToken() == null
                || !jwtService.esRefreshTokenValido(request.refreshToken())) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body((Object) Map.of("error", "Refresh token invalido o expirado")));
        }

        String username = jwtService.extraerUsuarioDeRefresh(request.refreshToken());

        // El refresh token solo vale si el usuario sigue dado de alta y activo.
        SecurityProperties.UserInfo usuario = securityProperties.getUsers().get(username);
        if (usuario == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body((Object) Map.of("error", "El usuario ya no existe")));
        }

        return Mono.just(ResponseEntity.ok()
                .body((Object) construirRespuesta(username, usuario.getRoles())));
    }

    /** Devuelve la identidad y los roles que viajan en el access token. */
    @GetMapping("/me")
    public Mono<ResponseEntity<Object>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .<ResponseEntity<Object>>map(auth -> ResponseEntity.ok(cuerpoMe(auth)))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body((Object) Map.of("error", "No autenticado")));
    }

    private Map<String, Object> cuerpoMe(Authentication auth) {
        return Map.of(
                "username", auth.getName(),
                "roles", auth.getAuthorities().stream()
                        .map(granted -> granted.getAuthority().replaceFirst("^ROLE_", ""))
                        .toList());
    }

    private ResponseEntity<Object> respuestaOk(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(granted -> granted.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        return ResponseEntity.ok().body((Object) construirRespuesta(auth.getName(), roles));
    }

    private LoginResponse construirRespuesta(String username, List<String> roles) {
        return new LoginResponse(
                jwtService.generarAccessToken(username, roles),
                jwtService.generarRefreshToken(username),
                "Bearer",
                jwtService.getExpirationSegundos(),
                username,
                roles);
    }

    private Mono<ResponseEntity<Object>> errorLogin() {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body((Object) Map.of("error", "Credenciales invalidas")));
    }

    public record LoginRequest(String username, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    /** @param expiresIn vida del access token, en segundos */
    public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresIn, String username, List<String> roles) {
    }
}
