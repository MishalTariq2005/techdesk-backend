package com.example.auth_service.rbac.service;

import com.example.auth_service.rbac.dto.AcceptInviteRequest;
import com.example.auth_service.rbac.dto.InviteUserRequest;
import com.example.auth_service.rbac.entity.UserInvitation;
import com.example.auth_service.rbac.repository.UserInvitationRepository;
import com.example.auth_service.rbac.security.CurrentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Mirrors the WelcomeEmailService pattern from tenant-service 3.3: MailHog SMTP,
 * one-time token, SHA-256 hashed at rest (same convention as refresh tokens —
 * see gotcha #17 lineage in the 3.2 notes).
 *
 * validateAndConsumeToken() only handles the invitation record itself; actual
 * User creation happens in UserManagementService.acceptInvite() via
 * User.fromAcceptedInvite(), using PermissionResolutionService to flatten the
 * invited role's permissions into the denormalized CSV column.
 */
@Service
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_HOURS = 72;

    private final UserInvitationRepository invitationRepository;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Same convention as AuthService — see its javadoc on this property. */
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public InvitationService(UserInvitationRepository invitationRepository,
                              JavaMailSender mailSender,
                              BCryptPasswordEncoder passwordEncoder) {
        this.invitationRepository = invitationRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void inviteUser(InviteUserRequest request) {
        invitationRepository.findByEmailAndStatus(request.email(), "PENDING")
            .ifPresent(existing -> existing.setStatus("REVOKED"));

        String rawToken = generateToken();
        String tokenHash = sha256Hex(rawToken);

        CurrentUserContext.Identity caller = CurrentUserContext.get();

        UserInvitation invitation = new UserInvitation();
        invitation.setEmail(request.email());
        invitation.setRole(request.role());
        invitation.setDepartment(request.department());
        invitation.setTokenHash(tokenHash);
        invitation.setInvitedBy(UUID.fromString(caller.userId()));
        invitation.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        invitationRepository.save(invitation);

        sendInviteEmail(request.email(), rawToken);
    }

    /**
     * Returns the invitation so the controller/UserManagementService can create
     * the actual User row inside the transactional boundary of the caller —
     * keeps this service focused on invitation-token lifecycle only.
     */
    @Transactional
    public UserInvitation validateAndConsumeToken(String rawToken) {
        String tokenHash = sha256Hex(rawToken);
        UserInvitation invitation = invitationRepository.findByTokenHashAndStatus(tokenHash, "PENDING")
            .orElseThrow(() -> new IllegalArgumentException("Invalid or already-used invitation token"));

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus("EXPIRED");
            throw new IllegalArgumentException("Invitation token has expired");
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(LocalDateTime.now());
        return invitation;
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private void sendInviteEmail(String toEmail, String rawToken) {
        String inviteLink = frontendBaseUrl + "/accept-invite?token=" + urlEncode(rawToken);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("You've been invited to TechDesk");
        message.setText("You've been invited to join your company's TechDesk workspace.\n\n"
            + "Accept your invitation and set your password: " + inviteLink + "\n\n"
            + "This invitation expires in " + EXPIRY_HOURS + " hours.");
        mailSender.send(message);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
