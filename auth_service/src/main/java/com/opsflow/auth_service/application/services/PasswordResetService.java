package com.opsflow.auth_service.application.services;

public interface PasswordResetService {

    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);
}
