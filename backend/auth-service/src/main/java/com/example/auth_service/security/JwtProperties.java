package com.example.auth_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de firma de los tokens, provistos por el Config Server (app.jwt.*).
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma de los access tokens (HS256). Minimo 32 caracteres. */
    private String secret;

    /** Secreto DISTINTO para los refresh tokens, para evitar el token confusion. */
    private String refreshSecret;

    /** Emisor del token. El gateway lo exige al validar. */
    private String issuer;

    /** Duracion del access token en milisegundos (15 min). */
    private long expiration;

    /** Duracion del refresh token en milisegundos (8 h). */
    private long refreshExpiration;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getRefreshSecret() {
        return refreshSecret;
    }

    public void setRefreshSecret(String refreshSecret) {
        this.refreshSecret = refreshSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }
}