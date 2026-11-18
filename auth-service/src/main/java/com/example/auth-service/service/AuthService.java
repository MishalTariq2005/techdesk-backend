package com.example.auth_service.service;
import com.example.auth_service.multitenancy.TenantContext;
import com.example.auth_service.dto.ForgotPasswordRequest;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.LogoutRequest;
import com.example.auth_service.dto.RefreshTokenRequest;
import com.example.auth_service.dto.ResetPasswordRequest;
import com.example.auth_service.entity.PasswordResetToken;
import com.example.auth_service.entity.RefreshToken;
import com.example.auth_service.entity.SecurityEvent;
import com.example.auth_service.entity.User;
import com.example.auth_service.exception.AccountLockedException;
import com.example.auth_service.exception.ReplayAttackException;
import com.example.auth_service.repository.PasswordResetTokenRepository;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.SecurityEventRepository;
import com.example.auth_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final long PASSWORD_RESET_TTL_MINUTES = 30;
    private static final String BLACKLIST_PREFIX = "refresh-token-blacklist:";

    // 6.4 account lockout: 5 failed attempts within 10 minutes locks the
    // account for 15 minutes. Keyed by tenant+email in Redis (already used
    // for the refresh-token blacklist) rather than a new DB column/migration —
    // this is transient security state, not a durable user attribute, and
    // Redis's native TTL is exactly the right tool for "N attempts within a
    // rolling window" without a cleanup job.
    private static final String FAILED_ATTEMPTS_PREFIX = "login-failures:";
    private static final String LOCKOUT_PREFIX = "login-lockout:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration FAILED_ATTEMPTS_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityEventRepository securityEventRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Base URL of the React frontend. Emails link here instead of exposing the
     * raw token-only instructions that used to tell people to call the API
     * directly. Overridable per-environment via app.frontend.base-url; defaults
     * to the Vite dev server so local dev works out of the box.
     */
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        SecurityEventRepository securityEventRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        StringRedisTemplate redisTemplate,
                        JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityEventRepository = securityEventRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String lockoutKey = lockoutKey(request.email());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            throw new AccountLockedException(
                    "Account temporarily locked due to repeated failed login attempts. Try again later.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(request.email(), user.getId());
            throw new BadCredentialsException("Invalid email or password");
        }

        clearFailedAttempts(request.email());

        String accessToken = jwtService.generateAccessToken(
                user.getId(), TenantContext.getCurrentTenant(), user.getRole(), user.getPermissionsList());

        IssuedRefreshToken issued = issueRefreshTokenInternal(user.getId());

        return new LoginResponse(
                accessToken, issued.rawToken(), "Bearer", jwtService.getAccessTokenExpirySeconds());
    }

    /**
     * Increments the failure counter (10-minute rolling window via Redis
     * TTL) and, on hitting MAX_FAILED_ATTEMPTS, sets a separate lockout key
     * with its own 15-minute TTL and logs a security event — same audit
     * posture as refresh-token replay detection.
     */
    private void recordFailedAttempt(String email, java.util.UUID userId) {
        String attemptsKey = attemptsKey(email);
        Long count = redisTemplate.opsForValue().increment(attemptsKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(attemptsKey, FAILED_ATTEMPTS_WINDOW);
        }
        if (count != null && count >= MAX_FAILED_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockoutKey(email), "locked", LOCKOUT_DURATION);
            securityEventRepository.save(new SecurityEvent(
                    "ACCOUNT_LOCKED", userId,
                    MAX_FAILED_ATTEMPTS + " failed login attempts within "
                            + FAILED_ATTEMPTS_WINDOW.toMinutes() + " minutes — locked for "
                            + LOCKOUT_DURATION.toMinutes() + " minutes"));
            log.warn("SECURITY EVENT: account locked for user {} after {} failed login attempts",
                    userId, count);
        }
    }

    private void clearFailedAttempts(String email) {
        redisTemplate.delete(attemptsKey(email));
    }

    private String attemptsKey(String email) {
        return FAILED_ATTEMPTS_PREFIX + TenantContext.getCurrentTenant() + ":" + email.toLowerCase();
    }

    private String lockoutKey(String email) {
        return LOCKOUT_PREFIX + TenantContext.getCurrentTenant() + ":" + email.toLowerCase();
    }

    /**
     * noRollbackFor is required: handleReplay() intentionally writes the mass-
     * revocation + SecurityEvent BEFORE this method throws ReplayAttackException.
     * Without this, Spring's default rollback-on-RuntimeException behavior would
     * silently undo those writes.
     */
    @Transactional(noRollbackFor = ReplayAttackException.class)
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = hash(request.refreshToken());

        RefreshToken presented = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (presented.isRevoked()) {
            handleReplay(presented);
            throw new ReplayAttackException(
                    "Refresh token reuse detected - all sessions for this account have been revoked");
        }

        if (presented.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        User user = userRepository.findById(presented.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));

        IssuedRefreshToken next = issueRefreshTokenInternal(user.getId());

        presented.setRevoked(true);
        presented.setReplacedByTokenId(next.entity().getId());
        refreshTokenRepository.save(presented);

        String accessToken = jwtService.generateAccessToken(
                user.getId(), TenantContext.getCurrentTenant(), user.getRole(), user.getPermissionsList());

        return new LoginResponse(
                accessToken, next.rawToken(), "Bearer", jwtService.getAccessTokenExpirySeconds());
    }

    /**
     * Revokes the presented refresh token in the DB and writes it to a Redis
     * blacklist with a TTL matching its remaining lifetime, per the roadmap's
     * explicit requirement. Idempotent/silent on an unknown token - logout never
     * leaks whether a given token existed.
     */
    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = hash(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);

            long ttlSeconds = Duration.between(Instant.now(), token.getExpiresAt()).toSeconds();
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + tokenHash, "revoked", Duration.ofSeconds(ttlSeconds));
            }

            log.info("User {} logged out, refresh token {} blacklisted", token.getUserId(), token.getId());
        });
    }

    /**
     * Always succeeds silently regardless of whether the email exists, to avoid
     * leaking which addresses are registered (user enumeration).
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String rawToken = generateOpaqueToken();
            PasswordResetToken resetToken = new PasswordResetToken(
                    user.getId(), hash(rawToken),
                    Instant.now().plus(PASSWORD_RESET_TTL_MINUTES, ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(resetToken);
            sendResetEmail(user, rawToken);
        });
    }

    /**
     * Validates and consumes a reset token, sets the new password, and - this part
     * isn't explicitly required by the roadmap, just good practice - revokes every
     * active refresh token for the user. A password reset is a credential change;
     * a device that already holds a valid refresh token shouldn't keep working
     * after the password "protecting" the account has changed.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hash(request.token());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Invalid or expired reset token");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        List<RefreshToken> activeTokens =
                refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        activeTokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(activeTokens);

        securityEventRepository.save(new SecurityEvent(
                "PASSWORD_RESET", user.getId(),
                activeTokens.size() + " active session(s) revoked due to password reset"));

        log.info("Password reset completed for user {}, {} session(s) revoked",
                user.getId(), activeTokens.size());
    }

    private void sendResetEmail(User user, String rawToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + urlEncode(rawToken);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("TechDesk - Password Reset Request");
        message.setText(
                "A password reset was requested for your account.\n\n"
                        + "Reset your password: " + resetLink + "\n\n"
                        + "This link expires in " + PASSWORD_RESET_TTL_MINUTES + " minutes. "
                        + "If you did not request this, you can ignore this email.");
        mailSender.send(message);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void handleReplay(RefreshToken reusedToken) {
        List<RefreshToken> stillActive =
                refreshTokenRepository.findAllByUserIdAndRevokedFalse(reusedToken.getUserId());
        stillActive.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(stillActive);

        SecurityEvent event = new SecurityEvent(
                "REFRESH_TOKEN_REUSE",
                reusedToken.getUserId(),
                "Reused refresh token id=" + reusedToken.getId()
                        + "; " + stillActive.size() + " active session(s) revoked");
        securityEventRepository.save(event);

        log.warn("SECURITY EVENT: refresh token reuse detected for user {} - {} session(s) revoked",
                reusedToken.getUserId(), stillActive.size());
    }

    /** Shared by login and refresh-token rotation. */
    private IssuedRefreshToken issueRefreshTokenInternal(UUID userId) {
        String rawToken = generateOpaqueToken();

        RefreshToken entity = new RefreshToken(
                userId,
                hash(rawToken),
                Instant.now(),
                Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS));

        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, entity);
    }

    /** High-entropy opaque token generator shared by refresh tokens and reset tokens. */
    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record IssuedRefreshToken(String rawToken, RefreshToken entity) {
    }
}