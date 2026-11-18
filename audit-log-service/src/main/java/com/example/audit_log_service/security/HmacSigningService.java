package com.example.audit_log_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Signs and verifies audit_logs rows using HMAC-SHA256 over
 * AuditLogEntry.canonicalPayload() (the 6.3 challenge's "integrity
 * verification"). A signature computed with a secret the caller doesn't
 * have cannot be forged to make a tampered row look legitimate — this is
 * what makes /api/audit-logs/verify a real forensic check rather than a
 * database SELECT the "tamperer" could just re-run.
 */
@Service
public class HmacSigningService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec keySpec;

    public HmacSigningService(@Value("${app.audit.hmac-secret}") String secret) {
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String canonicalPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute audit log HMAC signature", e);
        }
    }

    public boolean verify(String canonicalPayload, String expectedSignature) {
        if (expectedSignature == null) {
            return false;
        }
        String recomputed = sign(canonicalPayload);
        return constantTimeEquals(recomputed, expectedSignature);
    }

    /** Avoids leaking signature-match info via response-time side channels. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
