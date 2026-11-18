package com.example.notification_service.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Same pattern as asset-service's / file-service's RestTemplateConfig —
 * @LoadBalanced lets UserLookupClient address auth-service by its Eureka
 * service-id. RestTemplateBuilder lives in org.springframework.boot.restclient
 * in Spring Boot 4.x, not bundled into the web starter (see
 * spring-boot-starter-restclient in pom.xml).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
