package com.learning.security.services.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and renders HTML email templates from classpath resources.
 * Templates use {@code {{PLACEHOLDER}}} syntax for variable substitution.
 */
@Service
public class EmailTemplateService {

    @Value("${yousuf.app.name:Auth Forge}")
    private String appName;

    @Value("${yousuf.app.email.support-email:support@authserver.com}")
    private String supportEmail;

    @Value("${yousuf.app.email.brand-color:#4F46E5}")
    private String brandColor;

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public String buildVerificationEmail(String name, String otp, int expiryMinutes) {
        String template = loadTemplate("templates/email/verify-email.html");
        return applyCommonVariables(template)
            .replace("{{USER_NAME}}", name)
            .replace("{{OTP_CODE}}", otp)
            .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));
    }

    public String buildPasswordResetEmail(String name, String otp, int expiryMinutes) {
        String template = loadTemplate("templates/email/reset-password.html");
        return applyCommonVariables(template)
            .replace("{{USER_NAME}}", name)
            .replace("{{OTP_CODE}}", otp)
            .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryMinutes));
    }

    public String buildPasswordChangedEmail(String name) {
        String template = loadTemplate("templates/email/password-changed.html");
        return applyCommonVariables(template)
            .replace("{{USER_NAME}}", name);
    }

    public String buildWelcomeEmail(String name) {
        String template = loadTemplate("templates/email/welcome.html");
        return applyCommonVariables(template)
            .replace("{{USER_NAME}}", name);
    }

    private String applyCommonVariables(String template) {
        return template
            .replace("{{APP_NAME}}", appName)
            .replace("{{SUPPORT_EMAIL}}", supportEmail)
            .replace("{{BRAND_COLOR}}", brandColor)
            .replace("{{YEAR}}", String.valueOf(java.time.Year.now().getValue()));
    }

    private String loadTemplate(String path) {
        return templateCache.computeIfAbsent(path, key -> {
            try {
                ClassPathResource resource = new ClassPathResource(key);
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load email template: " + key, e);
            }
        });
    }
}
