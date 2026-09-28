package com.example.alumnos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuenta de monitoreo usada por Spring Boot Admin para leer /actuator/**
 * por HTTP Basic. La contrasena llega hasheada con BCrypt desde el Config Server.
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private MonitoringInfo monitoring = new MonitoringInfo();

    public MonitoringInfo getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringInfo monitoring) {
        this.monitoring = monitoring;
    }

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
