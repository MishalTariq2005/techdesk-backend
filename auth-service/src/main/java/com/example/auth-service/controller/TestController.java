package com.example.auth_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/auth/test")
    public String test(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
        return "Auth Service responded successfully! Tenant ID received: " + tenantId;
    }
}