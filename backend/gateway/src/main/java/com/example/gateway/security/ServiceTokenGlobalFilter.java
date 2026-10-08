package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Agrega la cabecera X-Service-Token a cada peticion que el gateway reenvia.
 *
 * <p>Los microservicios de negocio ya no validan el JWT del usuario, asi que esta
 * cabecera es lo UNICO que demuestra que una peticion viene del gateway. Sin ella,
 * POST localhost:8101/api/alumnos se ejecutaria sin ninguna comprobacion.</p>
 *
 * <p>El valor viene del Config Server (app.service-token.value), nunca esta escrito
 * en el codigo. Solo el gateway y los servicios lo conocen.</p>
 */
@Component
public class ServiceTokenGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Service-Token";

    private final ServiceTokenProperties properties;

    public ServiceTokenGlobalFilter(ServiceTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = properties.getValue();
        if (token == null || token.isBlank()) {
            // Fail closed: sin secreto no se reenvia nada. Dejar pasar la peticion
            // sin la cabecera la haria fallar en el microservicio, que es el
            // comportamiento correcto, pero lo mas claro es rechazarla aca.
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, token)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** Corre despues de la autenticacion de Spring Security, antes de enrutar. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}