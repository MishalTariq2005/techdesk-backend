package com.example.asset_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Same permitAll() treatment as every other TechDesk service (see Week 2
 * open-items #5): authorization is opt-in per-@RequiresPermission method,
 * not deny-by-default at the Spring Security layer. No PasswordEncoder
 * bean here — unlike auth-service, this service never hashes a password.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
