package com.no8do.api.auth;

import com.no8do.api.user.User;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String smtpHost;
    private final String from;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${no8do.frontend-url}") String frontendUrl,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${no8do.password-reset.from:}") String from
    ) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.smtpHost = smtpHost;
        this.from = from;
    }

    public boolean isConfigured() {
        return !smtpHost.isBlank() && !from.isBlank();
    }

    public void sendResetLink(User user, String token) {
        String resetUrl = frontendUrl.replaceAll("/+$", "")
            + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(from);
        message.setSubject("Redefina sua senha do No8do");
        message.setText("Recebemos um pedido para redefinir sua senha. Use este link: " + resetUrl);

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset is temporarily unavailable");
        }
    }
}
