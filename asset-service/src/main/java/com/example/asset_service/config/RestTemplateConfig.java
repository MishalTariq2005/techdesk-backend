package com.example.asset_service.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * NEW — no other TechDesk service makes an outbound service-to-service HTTP
 * call yet. @LoadBalanced lets UserLookupClient address auth-service by its
 * Eureka service-id ("AUTH-SERVICE") instead of a hardcoded host:port, the
 * same way the api-gateway's lb://AUTH-SERVICE routes do. Needs
 * spring-cloud-starter-loadbalancer AND spring-boot-starter-restclient on
 * the classpath (both added to this service's pom.xml) — in Spring Boot
 * 4.x, RestTemplateBuilder moved out of spring-boot-autoconfigure into its
 * own restclient module, package changed from
 * org.springframework.boot.web.client to org.springframework.boot.restclient.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}