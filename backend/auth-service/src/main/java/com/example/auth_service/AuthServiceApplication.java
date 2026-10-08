package com.example.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Servicio de identidad. Responsabilidades:
 *
 * <ol>
 *   <li>Verificar las credenciales de un usuario contra el repositorio de
 *       usuarios que le llega del Config Server.</li>
 *   <li>Firmar el access token y el refresh token.</li>
 *   <li>Emitir ambos tokens y devolverlos.</li>
 * </ol>
 *
 * <p>Nada mas. El gateway no valida contrasenas ni firma tokens: solo enruta y
 * aplica las reglas de autorizacion. Los microservicios de negocio no validan
 * tokens: solo exigen el token de servicio que prueba que la peticion viene del
 * gateway.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}