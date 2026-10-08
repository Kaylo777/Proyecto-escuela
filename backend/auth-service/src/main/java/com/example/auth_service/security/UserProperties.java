package com.example.auth_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Usuarios de la demo (app.security.users) y cuenta de monitoreo
 * (app.security.monitoring). Las contrasenas llegan hasheadas en BCrypt y nunca en
 * texto plano.
 *
 * <p>Este es el UNICO servicio que lee users: el gateway no las necesita porque no
 * valida contrasenas, solo enruta peticiones ya autenticadas.</p>
 */
@ConfigurationProperties(prefix = "app.security")
public class UserProperties {

    private Map<String, UserInfo> users = new LinkedHashMap<>();
    private MonitoringInfo monitoring = new MonitoringInfo();

    public Map<String, UserInfo> getUsers() {
        return users;
    }

    public void setUsers(Map<String, UserInfo> users) {
        this.users = users;
    }

    public MonitoringInfo getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringInfo monitoring) {
        this.monitoring = monitoring;
    }

    public static class UserInfo {
        private String password;
        private List<String> roles = new ArrayList<>();

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