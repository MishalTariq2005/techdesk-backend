package com.example.notification_service.multitenancy;

import com.example.notification_service.rbac.security.CurrentUserContextFilter;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MultiTenancyConfig {

    @Bean
    public FilterRegistrationBean<TenantIdentifierFilter> tenantFilterRegistration(
            TenantIdentifierFilter filter) {
        FilterRegistrationBean<TenantIdentifierFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CurrentUserContextFilter> currentUserContextFilterRegistration(
            CurrentUserContextFilter filter) {
        FilterRegistrationBean<CurrentUserContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

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
