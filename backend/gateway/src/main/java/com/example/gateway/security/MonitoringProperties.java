package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Solo la cuenta de monitoreo (app.security.monitoring), que usa Spring Boot Admin
 * para leer /actuator/** por HTTP Basic.
 *
 * <p>Ya NO estan los usuarios de la demo: eso vive en el auth-service. El gateway
 * no valida contrasenas ni firma tokens, asi que no tiene por que conocer las
 * credenciales de nadie.</p>
 */
@ConfigurationProperties(prefix = "app.security")
public class MonitoringProperties {

    private MonitoringInfo monitoring = new MonitoringInfo();

    public MonitoringInfo getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringInfo monitoring) {
        this.monitoring = monitoring;
    }

    /** Cuenta que Spring Boot Admin usa para leer /actuator/** por HTTP Basic. */
    public static class MonitoringInfo {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}