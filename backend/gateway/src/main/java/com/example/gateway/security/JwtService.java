package com.example.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Emite y valida los tokens JWT del gateway.
 *
 * <p>Se manejan dos tipos de token, firmados con secretos distintos:</br>
 * <ul>
 *   <li><b>access</b>: viaja en cada llamada (Authorization: Bearer). Corto,
 *       15 min, y es el que validan el gateway y los microservicios.</li>
 *   <li><b>refresh</b>: sirve solo para renovar el access token en
 *       /auth/refresh. Largo, 8 h, y nunca se acepta como acceso a la API.</li>
 * </ul>
 * La separacion de secretos evita el "token confusion": aunque alguien robe un
 * refresh token, no puede usarlo como credencial para llamar a /api/**.
 */
@Component
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TIPO = "typ";
    private static final String TIPO_ACCESS = "access";
    private static final String TIPO_REFRESH = "refresh";

    private final SecretKey claveAccess;
    private final SecretKey claveRefresh;
    private final String issuer;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(JwtProperties properties) {
        this.claveAccess = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.claveRefresh = Keys.hmacShaKeyFor(properties.getRefreshSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getIssuer();
        this.expirationMs = properties.getExpiration();
        this.refreshExpirationMs = properties.getRefreshExpiration();
    }

    // ---------------------------------------------------------------- emisión

    public String generarAccessToken(String username, List<String> roles) {
        Date ahora = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TIPO, TIPO_ACCESS)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                // El algoritmo se fija explicitamente: si se dejara que jjwt lo
                // elija solo, firmaria con HS384 segun el largo de la clave y los
                // validadores (HS256) rechazarian el token.
                .signWith(claveAccess, Jwts.SIG.HS256)
                .compact();
    }

    public String generarRefreshToken(String username) {
        Date ahora = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + refreshExpirationMs))
                .signWith(claveRefresh, Jwts.SIG.HS256)
                .compact();
    }

    public long getExpirationSegundos() {
        return expirationMs / 1000;
    }

    // -------------------------------------------------------------- validación

    public boolean esAccessTokenValido(String token) {
        try {
            Claims claims = leerAccess(token);
            return TIPO_ACCESS.equals(claims.get(CLAIM_TIPO, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean esRefreshTokenValido(String token) {
        try {
            Claims claims = leerRefresh(token);
            return TIPO_REFRESH.equals(claims.get(CLAIM_TIPO, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** El refresh token debe seguir siendo de un usuario que exista y siga vigente. */
    public String extraerUsuarioDeRefresh(String token) {
        return leerRefresh(token).getSubject();
    }

    public String extraerUsuario(String token) {
        return leerAccess(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extraerRoles(String token) {
        Object roles = leerAccess(token).get(CLAIM_ROLES);
        if (roles instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Convierte los roles del token (ADMIN, USER) en autoridades de Spring
     * Security (ROLE_ADMIN, ROLE_USER), que es lo que espera hasRole(...).
     */
    public List<GrantedAuthority> extraerAutoridades(String token) {
        return extraerRoles(token).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
    }

    private Claims leerAccess(String token) {
        return Jwts.parser()
                .verifyWith(claveAccess)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims leerRefresh(String token) {
        return Jwts.parser()
                .verifyWith(claveRefresh)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
