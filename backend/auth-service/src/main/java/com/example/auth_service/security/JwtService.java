package com.example.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Firma y valida los tokens JWT. Solo este servicio EMITE tokens: el gateway
 * unicamente los valida.
 *
 * <p>Se manejan dos tipos de token, firmados con secretos distintos:</p>
 * <ul>
 *   <li><b>access</b>: viaja en cada llamada (Authorization: Bearer). Corto, 15
 *       min, y es el que valida el gateway.</li>
 *   <li><b>refresh</b>: sirve solo para renovar el access token en
 *       /auth/refresh. Largo, 8 h, y nunca se acepta como acceso a la API.</li>
 * </ul>
 * <p>La separacion de secretos evita el "token confusion": aunque alguien robe un
 * refresh token, no puede usarlo como credencial para llamar a /api/**.</p>
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

    private Claims leerRefresh(String token) {
        return Jwts.parser()
                .verifyWith(claveRefresh)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}