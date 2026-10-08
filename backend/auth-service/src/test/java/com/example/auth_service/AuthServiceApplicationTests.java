package com.example.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Solo verifica que el contexto de Spring arranque. Requiere el Config Server y
 * Eureka levantados; por eso el POM agregador corre los tests con skipTests=true
 * y se ejecutan a mano con .\mvnw.cmd test.
 */
@SpringBootTest
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}