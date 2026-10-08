package com.example.auth_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secreto compartido entre el gateway y los microservicios (app.service-token.*).
 *
 * <p>Es lo que responde a la weakest-link del diseno: si los microservicios no
 * validan el JWT del usuario, al menos exigen prueba de que la peticion salio del
 * gateway. Sin esto, cualquiera con un token valido -- o con ninguno -- podria
 * llamar directo a localhost:8101 y saltearse toda la cadena de seguridad.</p>
 */
@ConfigurationProperties(prefix = "app.service-token")
public class ServiceTokenProperties {

    private String value;

    /** Si viene vacio, el filtro rechaza TODO (fail closed) en vez de abrir la puerta. */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}