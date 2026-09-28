package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de emision de tokens. Se leen del Config Server (app.jwt.*) para
 * que el gateway y todos los microservicios compartan los mismos valores.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma de los access tokens. */
    private String secret;

    /** Secreto de firma de los refresh tokens (distinto del de acceso). */
    private String refreshSecret;

    /** Emisor del token, validado por todos los servicios. */
    private String issuer;

    /** Duracion del access token en milisegundos. */
    private long expiration;

    /** Duracion del refresh token en milisegundos. */
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
