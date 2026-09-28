package com.example.administracion.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * El microservicio administracion como RESOURCE SERVER.
 *
 * <p>No confÃ­a en el gateway: valida por su cuenta la firma, el emisor y la
 * caducidad del access token. Asi, aunque alguien conozca el puerto 8102 y llame
 * directo al servicio (saltandose el gateway), la API sigue protegida.</p>
 *
 * <p>Defensa en profundidad, en dos niveles:</p>
 * <ol>
 *   <li><b>Por URL</b>: las reglas de esta clase (GET para cualquier
 *       autenticado, escritura solo ADMIN).</li>
 *   <li><b>Por metodo</b>: @PreAuthorize en el controlador.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
public class SecurityConfig {

    /**
     * Cadena 1: endpoints de actuator. No forman parte de la API de negocio, asi
     * que se autentican con HTTP Basic y la cuenta de monitoreo que usa Spring
     * Boot Admin. Se separan en su propia cadena para no mezclarlos con el JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /** Cadena 2: la API de negocio, protegida con JWT. */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                               JwtDecoder jwtDecoder,
                                               JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                // Spring reenvia a /error (DispatcherType.ERROR) cuando una peticion
                // falla por cualquier motivo (400, 404, 500...). Sin esta excepcion,
                // un error de la app terminaria en el denyAll() de abajo y se
                // responderia 403 "sin permisos", enmascarando el error real.
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(escribirJson(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(escribirJson(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(responderJson(HttpStatus.FORBIDDEN)))
                .build();
    }

    /**
     * Valida el access token con el secreto compartido del Config Server
     * (HS256) y comprueba tambien el emisor.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    /** Claim "roles" (ADMIN) -> autoridad ROLE_ADMIN, que es lo que usa hasRole(...). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties properties) {
        SecurityProperties.MonitoringInfo monitoring = properties.getMonitoring();
        UserDetails usuario = User.withUsername(monitoring.getUsername())
                .password(monitoring.getPassword())
                .roles(monitoring.getRoles().toArray(new String[0]))
                .build();
        return new InMemoryUserDetailsManager(usuario);
    }

    /** 401 con cuerpo JSON, para que el cliente distinga "sin token" de "sin permiso". */
    private AuthenticationEntryPoint escribirJson(HttpStatus status) {
        return (request, response, ex) -> {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(cuerpoJson(status));
        };
    }

    private AccessDeniedHandler responderJson(HttpStatus status) {
        return (request, response, ex) -> {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(cuerpoJson(status));
        };
    }

    private String cuerpoJson(HttpStatus status) {
        String mensaje = status == HttpStatus.UNAUTHORIZED
                ? "Token ausente, invalido o expirado."
                : "No tiene permisos para esta operacion.";
        return "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + mensaje + "\"}";
    }
}
