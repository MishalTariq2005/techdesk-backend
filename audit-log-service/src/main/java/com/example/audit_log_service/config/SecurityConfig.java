package com.example.audit_log_service.config;

import com.example.audit_log_service.security.CurrentUserContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Actual authorization happens in AuditLogController (AUDIT_VIEW check) and
 * the gateway's GatewayRoleCheckFilter (already restricts /api/audit-logs to
 * IT_MANAGER/COMPANY_ADMIN/AUDITOR before it even reaches here) — same
 * permitAll()-at-Spring-Security-layer pattern every other service in this
 * project uses; this filter chain just disables CSRF for a stateless JWT API
 * and lets CurrentUserContextFilter do the real identity parsing.
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

    @Bean
    public FilterRegistrationBean<CurrentUserContextFilter> currentUserContextFilterRegistration(
            CurrentUserContextFilter filter) {
        FilterRegistrationBean<CurrentUserContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
