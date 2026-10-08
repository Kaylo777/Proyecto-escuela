package com.example.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige la cabecera X-Service-Token en TODAS las peticiones. No usa Spring
 * Security: es un filtro servlet plano, precisamente para que este servicio no
 * tenga configuracion de seguridad copiada (el gateway es la unica autoridad).
 *
 * <p>El gateway agrega esa cabecera en cada request que reenvia. Si alguien entra
 * directo por el puerto de este servicio sin la cabecera, recibe 403.</p>
 */
@Component
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final ServiceTokenProperties properties;

    public ServiceTokenFilter(ServiceTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String esperado = properties.getValue();
        String recibido = request.getHeader(HEADER);

        // Fail closed: sin secreto configurado se rechaza todo, nunca se acepta.
        if (esperado == null || esperado.isBlank()) {
            responder(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Token de servicio no configurado en este servicio.");
            return;
        }
        if (recibido == null || !comparacionSegura(recibido, esperado)) {
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
        response.getWriter().write(
                "{\"status\":" + status + ",\"message\":\"" + mensaje + "\"}");
    }
}