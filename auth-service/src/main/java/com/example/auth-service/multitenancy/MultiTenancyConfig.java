package com.example.auth_service.multitenancy;

import com.example.auth_service.rbac.security.CurrentUserContextFilter;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MultiTenancyConfig {

    /**
     * Registers TenantIdentifierFilter with the highest possible
     * precedence so it runs before Spring Security's filter chain.
     * Without this explicit ordering, Spring Boot's default filter
     * ordering is not guaranteed to put it early enough — and login
     * specifically depends on the tenant being resolved before
     * UserRepository is ever touched.
     */
    @Bean
    public FilterRegistrationBean<TenantIdentifierFilter> tenantFilterRegistration(
            TenantIdentifierFilter filter) {
        FilterRegistrationBean<TenantIdentifierFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Runs immediately after TenantIdentifierFilter. If tenant resolution
     * rejected the request (missing/invalid X-Tenant-ID), this never runs —
     * HIGHEST_PRECEDENCE + 1 guarantees it's still second in line regardless
     * of what other filters get added later. Populates CurrentUserContext
     * (RBAC layers 2 and 3 both read from it) for the lifetime of the request,
     * clearing it in a finally block same as TenantContext.
     */
    @Bean
    public FilterRegistrationBean<CurrentUserContextFilter> currentUserContextFilterRegistration(
            CurrentUserContextFilter filter) {
        FilterRegistrationBean<CurrentUserContextFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Wires the schema-based multi-tenancy SPIs into Hibernate. This is
     * the piece that actually turns on multi-tenancy — without it, the
     * resolver and connection provider beans above exist but Hibernate
     * never calls them.
     */
    @Bean
    public HibernatePropertiesCustomizer multiTenancyHibernatePropertiesCustomizer(
            HibernateTenantIdentifierResolver tenantIdentifierResolver,
            SchemaMultiTenantConnectionProvider connectionProvider) {
        return hibernateProperties -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        };
    }
}