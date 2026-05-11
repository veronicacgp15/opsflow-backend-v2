package com.opsflow.auth_service.application.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        String base = frontendBaseUrl.replaceAll("/$", "");
        if (base.endsWith("/auth")) {
            base = base.substring(0, base.length() - "/auth".length());
        }
        this.frontendBaseUrl = base.replaceAll("/$", "");
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationUrl = frontendBaseUrl + "/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Verificación de cuenta - OpsFlow");

        String emailBody = "Estimado/a usuario/a,\n\n" +
                "Gracias por registrarse en OpsFlow. Para completar el proceso de configuración de su cuenta " +
                "y garantizar la seguridad de su acceso, es necesario validar su dirección de correo electrónico.\n\n" +
                "Por favor, haga clic en el siguiente enlace para verificar su cuenta:\n" +
                verificationUrl + "\n\n" +
                "Si usted no ha solicitado este registro, puede ignorar este mensaje de forma segura.\n\n" +
                "Atentamente,\n" +
                "El equipo de OpsFlow";

        message.setText(emailBody);

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = frontendBaseUrl + "/auth/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Restablecer contrasena - OpsFlow");

        String emailBody = "Estimado/a usuario/a,\n\n"
                + "Recibimos una solicitud para restablecer la contrasena de su cuenta en OpsFlow.\n\n"
                + "Si fue usted, haga clic en el siguiente enlace (vence en 1 hora):\n"
                + resetUrl
                + "\n\n"
                + "Si no solicito este cambio, ignore este mensaje; su contrasena no se modificara.\n\n"
                + "Atentamente,\n"
                + "El equipo de OpsFlow";

        message.setText(emailBody);

        mailSender.send(message);
    }
}
