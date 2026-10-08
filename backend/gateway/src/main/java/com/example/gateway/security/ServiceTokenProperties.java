package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secreto compartido entre el gateway y los microservicios
 * (app.service-token.value), provisto por el Config Server.
 *
 * <p>El gateway lo agrega a cada peticion reenviada; los microservicios lo
 * exigen. Es la credencial de servicio a servicio.</p>
 */
@ConfigurationProperties(prefix = "app.service-token")
public class ServiceTokenProperties {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}