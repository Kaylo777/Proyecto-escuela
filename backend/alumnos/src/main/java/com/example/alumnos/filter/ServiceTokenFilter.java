package com.example.alumnos.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige la cabecera X-Service-Token en TODAS las peticiones.
 *
 * <p><b>Por que este microservicio no tiene Spring Security:</b> el gateway es la
 * unica autoridad de seguridad del sistema. El emite los tokens, valida la firma
 * y aplica las reglas por rol. Repetir esa validacion aca seria duplicar la
 * configuracion en cada servicio, que es justo lo que hace fragile una arquitectura
 * de microservicios.</p>
 *
 * <p><b>Y entonces, como se protege?</b> Con esta cabecera. El gateway la agrega a
 * todo lo que reenvia, y el secreto viene del Config Server. Sin la cabecera, este
 * servicio responde 403: entrar por localhost:8101 con Postman ya no alcanza,
 * porque ademas habria que conocer el secreto de servicio.</p>
 *
 * <p>No usa Spring Security a proposito: es un filtro servlet plano, para que este
 * servicio no dependa de la libreria de seguridad.</p>
 *
 * <p><b>Exencion de actuator:</b> Spring Boot Admin y los Eureka probes tienen que
 * consultar /actuator/health, y no conocen el secreto de servicio. Esas rutas se
 * dejan pasar, pero no quedan al alcance de la red: el puerto de management se
 * ata a 127.0.0.1 (ver management.server.address en el Config Server), de modo
 * que solo los procesos de esta misma maquina pueden alcanzarlas. Ese es el
 * reemplazo de la proteccion que antes daba Spring Security.</p>
 */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final String tokenEsperado;

    public ServiceTokenFilter(@Value("${app.service-token.value:}") String tokenEsperado) {
        this.tokenEsperado = tokenEsperado;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (tokenEsperado == null || tokenEsperado.isBlank()) {
            // Fail closed: sin secreto configurado se rechaza TODO, nunca se acepta.
            responder(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Token de servicio no configurado en este servicio.");
            return;
        }

        String recibido = request.getHeader(HEADER);
        if (recibido == null || !comparacionSegura(recibido, tokenEsperado)) {
            responder(response, HttpServletResponse.SC_FORBIDDEN,
                    "Acceso directo no permitido: use el API Gateway.");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Comparacion en tiempo constante. Con equals() un atacante podria medir el
     * tiempo de respuesta e ir adivinando el secreto caracter por caracter.
     */
    private boolean comparacionSegura(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private void responder(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + mensaje + "\"}");
    }
}