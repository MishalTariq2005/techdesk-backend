package com.example.notification_service.notification.service;

import com.example.notification_service.client.UserLookupClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Same SimpleMailMessage + JavaMailSender mechanism as auth-service's
 * InvitationService.sendInviteEmail() — MailHog SMTP, no auth needed.
 * Best-effort by design (see UserLookupClient's own doc comment): a failed
 * email send here never fails the notification-creation request, since the
 * DB row and SSE push happen independently in NotificationDispatchService.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final UserLookupClient userLookupClient;

    public EmailNotificationService(JavaMailSender mailSender, UserLookupClient userLookupClient) {
        this.mailSender = mailSender;
        this.userLookupClient = userLookupClient;
    }

    public void sendNotificationEmail(UUID recipientUserId, String title, String message) {
        userLookupClient.findUserById(recipientUserId).ifPresentOrElse(
            user -> {
                try {
                    SimpleMailMessage mailMessage = new SimpleMailMessage();
                    mailMessage.setTo(user.email());
                    mailMessage.setSubject("TechDesk: " + title);
                    mailMessage.setText(message);
                    mailSender.send(mailMessage);
                } catch (Exception e) {
                    log.warn("Failed to send notification email to {} — notification still recorded and pushed via SSE",
                        recipientUserId, e);
                }
            },
            () -> log.warn("Could not resolve email for user {} — skipping email send, notification still recorded", recipientUserId)
        );
    }
}
