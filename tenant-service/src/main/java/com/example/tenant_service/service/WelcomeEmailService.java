package com.example.tenant_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class WelcomeEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@techdesk.local}")
    private String fromAddress;

    public WelcomeEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Plaintext temp password in an email is fine for local dev against
     * MailHog; this is exactly the kind of thing 6.4 (Security Hardening)
     * exists to revisit before anything resembling production. Don't
     * pre-harden it now.
     */
    public void sendWelcomeEmail(String toEmail, String companyName, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Welcome to TechDesk — " + companyName + " is ready");
        message.setText(
                "Your TechDesk workspace for " + companyName + " has been created.\n\n" +
                "Temporary password: " + tempPassword + "\n\n" +
                "You will be required to change this password on first login.\n\n" +
                "— TechDesk Team"
        );
        mailSender.send(message);
    }
}
