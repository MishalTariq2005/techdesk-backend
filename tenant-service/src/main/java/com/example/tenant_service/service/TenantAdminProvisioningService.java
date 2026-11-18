package com.example.tenant_service.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class TenantAdminProvisioningService {

    private static final String ALLOWED_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;

    public TenantAdminProvisioningService(JdbcTemplate jdbcTemplate, BCryptPasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Inserts the default COMPANY_ADMIN user directly into the new tenant
     * schema's OWN users table — NOT the shared auth.users table auth-service
     * currently reads at login. That split is intentional: real per-tenant
     * schema isolation for user data is the target architecture, and 3.4's
     * Hibernate multi-tenancy resolver is what reconciles auth-service's
     * login path with it. Until 3.4 lands, a user created here cannot log in
     * through the existing /api/auth/login endpoint yet — that gap is
     * expected, not a bug in this code.
     *
     * Returns the generated temporary password so the caller can email it;
     * it is never persisted or logged in plaintext.
     */
    public String createDefaultAdmin(String schemaName, String email, String firstName, String lastName) {
        String tempPassword = generateTempPassword();
        String hash = passwordEncoder.encode(tempPassword);

        jdbcTemplate.update(
                "INSERT INTO \"" + schemaName + "\".users " +
                        "(email, password_hash, first_name, last_name, role, status, must_change_password, permissions) " +
                        "VALUES (?, ?, ?, ?, 'COMPANY_ADMIN', 'ACTIVE', TRUE, ?)",
                email, hash, firstName, lastName, ""
        );

        
        return tempPassword;
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALLOWED_CHARS.charAt(RANDOM.nextInt(ALLOWED_CHARS.length())));
        }
        return sb.toString();
    }
}
