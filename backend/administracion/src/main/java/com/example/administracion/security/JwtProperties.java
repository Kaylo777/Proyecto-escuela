package com.example.administracion.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de validacion del token, leidos del Config Server (app.jwt.*).
 *
 * <p>Deben ser IDENTICOS a los del gateway: ese es el mecanismo que hace que la
 * seguridad viaje con la peticion y no dependa de pasar (o no) por el gateway.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secreto de firma de los access tokens. */
    private String secret;

    /** Emisor del token, validado aqui para rechazar tokens de otra aplicacion. */
    private String issuer;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
