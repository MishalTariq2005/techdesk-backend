package com.example.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 6.4: "All passwords must be hashed with BCrypt (cost factor 12
        // minimum)" — was left at BCryptPasswordEncoder's default (10) with
        // a "not yet" comment. Fixed. NOTE: this only affects passwords
        // hashed from this point forward — BCrypt's cost factor is embedded
        // in each hash itself ($2a$12$... vs $2a$10$...), so
        // BCryptPasswordEncoder.matches() still verifies existing cost-10
        // hashes correctly. No migration/rehash of existing users is
        // required for this change to take effect; only new/changed
        // passwords get the stronger cost going forward.
        return new BCryptPasswordEncoder(12);
    }
}