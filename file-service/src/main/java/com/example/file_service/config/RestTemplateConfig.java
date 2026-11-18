package com.example.file_service.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Same pattern as asset-service's RestTemplateConfig — @LoadBalanced lets
 * TicketLookupClient and GadgetRequestLookupClient address ticket-service
 * and asset-service by their Eureka service-ids rather than hardcoded
 * host:port. Needs spring-cloud-starter-loadbalancer AND
 * spring-boot-starter-restclient on the classpath (both in this service's
 * pom.xml) — RestTemplateBuilder lives in org.springframework.boot.restclient
 * in Spring Boot 4.x, not bundled into the web starter.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
