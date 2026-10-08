package com.example.auth_service.service;

import com.example.auth_service.security.UserProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resuelve las credenciales desde la configuracion que entrega el Config Server
 * (app.security.users). Las contrasenas llegan hasheadas en BCrypt, asi que este
 * servicio nunca ve una contrasena en texto plano: solo compara el hash.
 */
@Service
public class UserDetailsServiceFromConfig implements UserDetailsService {

    private final UserProperties properties;

    public UserDetailsServiceFromConfig(UserProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, UserProperties.UserInfo> usuarios = properties.getUsers();

        UserProperties.UserInfo info = usuarios.get(username);
        if (info == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }

        return User.withUsername(username)
                .password(info.getPassword())
                .roles(info.getRoles().toArray(new String[0]))
                .build();
    }

    public boolean existe(String username) {
        return properties.getUsers().containsKey(username);
    }

    public java.util.List<String> rolesDe(String username) {
        UserProperties.UserInfo info = properties.getUsers().get(username);
        return info == null ? java.util.List.of() : info.getRoles();
    }
}