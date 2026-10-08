package com.example.gateway.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad del API Gateway (WebFlux / reactiva).
 *
 * <p><b>El gateway es la unica autoridad de seguridad del sistema.</b> Sus tres
 * funciones, y ninguna mas:</p>
 * <ol>
 *   <li><b>Valida</b> la firma del access token en cada peticion. No lo firma:
 *       eso lo hace el auth-service.</li>
 *   <li><b>Autoriza</b> por rol: GET /api/** para cualquier usuario autenticado,
 *       y POST / PUT / DELETE solo para ROLE_ADMIN.</li>
 *   <li><b>Enruta</b>, aggregate la cabecera X-Service-Token para que los
 *       microservicios de negocio acepten solo lo que pasa por aca.</li>
 * </ol>
 *
 * <p>Los usuarios y sus contrasenas NO estan aqui: viven en el auth-service. Y los
 * microservicios de negocio no tienen Spring Security: son de confianza, y se
 * defienden con el token de servicio.</p>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({MonitoringProperties.class, JwtProperties.class, ServiceTokenProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                          ReactiveJwtAuthenticationConverter jwtAuthenticationConverter) {
        return http
                // API stateless: sin sesion ni cookies, solo tokens. En WebFlux el
                // repositorio de contexto "NoOp" es el equivalente a
                // SessionCreationPolicy.STATELESS.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                // Sin httpBasic() aqui a proposito: la cuenta de monitoreo solo
                // existe en la cadena de /actuator/** (ver monitoringSecurityWebFilterChain).
                // Si se activara en esta cadena, un "Basic monitor:..." pasaria el
                // filtro del gateway con ROLE_ADMIN sobre /api/**.
                .authorizeExchange(authorize -> authorize
                        // El preflight CORS no lleva token: se responde antes de autorizar.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // El login y el refresh los resuelve el auth-service: el
                        // gateway solo los deja pasar. Quien aun no tiene token no
                        // puede exigir uno.
                        .pathMatchers("/auth/login", "/auth/refresh").permitAll()
                        // AQUI VIVE LA MATRIZ DE ROLES. Antes cada microservicio
                        // repetia estas reglas con @PreAuthorize; ahora que los
                        // servicios no tienen Spring Security, esta es la unica
                        // barrera. Si alguien la saca, un token USER podria
                        // escribir en la API.
                        .pathMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").authenticated()
                        .pathMatchers("/auth/**").authenticated()
                        // Spring reenvia a /error (DispatcherType.ERROR) cuando una
                        // peticion falla por cualquier motivo (400, 404, 500...). Sin
                        // esta excepcion, un error de la app se responderia con 403
                        // "sin permisos", enmascarando el error real.
                        .pathMatchers("/error").permitAll()
                        // Deny by default: lo que no este declarado arriba, se rechaza.
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.FORBIDDEN)))
                .build();
    }

    /**
     * Cadena exclusiva de /actuator/** (@Order(1): se evalua antes que la del
     * gateway). Spring Boot Admin lee metricas, beans y logs con HTTP Basic
     * usando la cuenta de monitoreo, que vive en SU PROPIO
     * authenticationManager: asi esa cuenta de servicio nunca puede autenticarse
     * en /auth/login ni obtener un token de usuario con permisos.
     *
     * <p>Al vivir en una cadena separada, el HTTP Basic no alcanza para autorizar
     * nada fuera de /actuator/**.</p>
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain monitoringSecurityWebFilterChain(
            ServerHttpSecurity http,
            @Qualifier("monitoringAuthenticationManager") ReactiveAuthenticationManager monitoringAuthenticationManager) {
        return http
                // securityMatcher es obligatorio: sin el, una cadena de WebFlux se
                // aplica a TODAS las peticiones y su denyAll() taparia /auth/login.
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/actuator/**"))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(basic -> basic.authenticationManager(monitoringAuthenticationManager))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        .anyExchange().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((exchange, ex) ->
                                escribirJson(exchange, HttpStatus.FORBIDDEN)))
                .build();
    }

    /** Convierte el claim "roles" del token (ADMIN) en autoridad ROLE_ADMIN. */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(rolesConverter));
        return converter;
    }

    /**
     * Valida la firma del access token con el secreto que le entrega el Config
     * Server. El gateway SOLO valida: nunca firma, asi que no necesita la
     * libreria jjwt ni el secreto de refresh.
     *
     * <p>En WebFlux el decoder debe ser REACTIVO (ReactiveJwtDecoder); el
     * JwtDecoder de la API servlet no sirve aqui.</p>
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    /**
     * Cuenta de servicio SOLO para /actuator/** (Spring Boot Admin). Al vivir en
     * un authenticationManager aparte, no se puede usar para iniciar sesion: un
     * atacante que se entere de estas credenciales no obtiene un token de usuario
     * con permisos de ADMIN.
     */
    @Bean
    public ReactiveAuthenticationManager monitoringAuthenticationManager(MonitoringProperties properties) {
        MonitoringProperties.MonitoringInfo monitoring = properties.getMonitoring();
        UserDetails usuario = User.withUsername(monitoring.getUsername())
                .password(monitoring.getPassword())
                .roles(monitoring.getRoles().toArray(new String[0]))
                .build();
        return new UserDetailsRepositoryReactiveAuthenticationManager(
                new MapReactiveUserDetailsService(usuario));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Respuestas de error en JSON, para que el frontend Angular pueda leerlas. */
    private Mono<Void> escribirJson(ServerWebExchange exchange, HttpStatus status) {
        String mensaje = status == HttpStatus.UNAUTHORIZED
                ? "Token ausente, invalido o expirado. Inicie sesion otra vez."
                : "No tiene permisos para esta operacion.";
        byte[] cuerpo = ("{\"status\":" + status.value() + ",\"error\":\""
                + status.getReasonPhrase() + "\",\"message\":\"" + mensaje + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(cuerpo)));
    }
}