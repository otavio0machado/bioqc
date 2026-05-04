package com.bioqc.service;

import com.bioqc.dto.request.ForgotPasswordRequest;
import com.bioqc.dto.request.ResetPasswordRequest;
import com.bioqc.dto.response.PasswordResetResponse;
import com.bioqc.entity.PasswordResetToken;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.repository.PasswordResetTokenRepository;
import com.bioqc.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String GENERIC_MESSAGE =
        "Se o e-mail estiver cadastrado, enviaremos as instruções de recuperação.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.path:/reset-password}")
    private String resetPath;

    @Value("${app.password-reset.token-ttl-minutes:60}")
    private long tokenTtlMinutes;

    @Value("${app.password-reset.expose-link-in-response:false}")
    private boolean exposeLinkInResponse;

    @Value("${app.password-reset.from-email:${spring.mail.username:no-reply@bioqc.local}}")
    private String fromEmail;

    public PasswordResetService(
        UserRepository userRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        ObjectProvider<JavaMailSender> mailSenderProvider
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Transactional
    public PasswordResetResponse requestReset(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return new PasswordResetResponse(GENERIC_MESSAGE, null);
        }

        invalidateOpenTokens(user.getId());

        String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(tokenTtlMinutes, ChronoUnit.MINUTES);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
            .user(user)
            .token(token)
            .expiresAt(expiresAt)
            .build());

        String resetUrl = buildResetUrl(token);
        sendResetEmail(user, resetUrl);

        return new PasswordResetResponse(
            GENERIC_MESSAGE,
            exposeLinkInResponse ? resetUrl : null
        );
    }

    @Transactional
    public PasswordResetResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(request.token().trim())
            .orElseThrow(() -> new BusinessException("Link de recuperação inválido ou expirado."));

        if (passwordResetToken.getUsedAt() != null || passwordResetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Link de recuperação inválido ou expirado.");
        }

        User user = passwordResetToken.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("Usuário inativo.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        passwordResetToken.setUsedAt(Instant.now());
        invalidateOpenTokens(user.getId());
        passwordResetToken.setUsedAt(Instant.now());

        return new PasswordResetResponse("Senha redefinida com sucesso.", null);
    }

    private void invalidateOpenTokens(UUID userId) {
        List<PasswordResetToken> openTokens = passwordResetTokenRepository.findByUser_IdAndUsedAtIsNull(userId);
        Instant now = Instant.now();
        for (PasswordResetToken token : openTokens) {
            token.setUsedAt(now);
        }
    }

    private String buildResetUrl(String token) {
        String normalizedFrontendUrl = frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1)
            : frontendUrl;
        String normalizedResetPath = resetPath.startsWith("/") ? resetPath : "/" + resetPath;
        return normalizedFrontendUrl + normalizedResetPath + "?token=" + token;
    }

    private void sendResetEmail(User user, String resetUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(fromEmail)) {
            LOGGER.info("Link de recuperação gerado para {}: {}", user.getEmail(), resetUrl);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject("Recuperação de senha - Biodiagnóstico 4.0");
            message.setText("""
                Olá,

                Recebemos uma solicitação para redefinir sua senha.

                Use o link abaixo para continuar:
                %s

                Este link expira em %d minutos.

                Se você não solicitou esta alteração, ignore este e-mail.
                """.formatted(resetUrl, tokenTtlMinutes).trim());
            mailSender.send(message);
        } catch (Exception exception) {
            LOGGER.warn("Não foi possível enviar o e-mail de recuperação para {}. Link: {}", user.getEmail(), resetUrl, exception);
        }
    }
}
