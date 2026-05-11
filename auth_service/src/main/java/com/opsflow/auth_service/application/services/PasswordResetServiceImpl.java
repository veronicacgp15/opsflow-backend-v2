package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.domain.ports.out.UserRepositoryPort;
import com.opsflow.auth_service.infrastructure.entities.PasswordResetToken;
import com.opsflow.auth_service.infrastructure.entities.User;
import com.opsflow.auth_service.infrastructure.repositories.PasswordResetTokenRepository;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepositoryPort userRepositoryPort;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserService userService;
    private final EmailService emailService;

    public PasswordResetServiceImpl(
            UserRepositoryPort userRepositoryPort,
            UserRepository userRepository,
            PasswordResetTokenRepository resetTokenRepository,
            UserService userService,
            EmailService emailService) {
        this.userRepositoryPort = userRepositoryPort;
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.userService = userService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        userRepositoryPort
                .findByEmail(email)
                .ifPresent(
                        userDomain -> {
                            User user =
                                    userRepository
                                            .findById(userDomain.getId())
                                            .orElseThrow();
                            resetTokenRepository.deleteByUser_Id(user.getId());
                            String token = UUID.randomUUID().toString();
                            resetTokenRepository.save(new PasswordResetToken(token, user));
                            emailService.sendPasswordResetEmail(user.getEmail(), token);
                        });
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt =
                resetTokenRepository
                        .findByToken(token)
                        .orElseThrow(() -> new IllegalArgumentException("Token invalido o expirado."));
        if (prt.getExpiryDate().isBefore(LocalDateTime.now())) {
            resetTokenRepository.delete(prt);
            throw new IllegalArgumentException("El enlace ha expirado.");
        }
        User user = prt.getUser();
        userService.changePassword(user.getId(), newPassword);
        resetTokenRepository.delete(prt);
    }
}
