package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.infrastructure.entities.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {

    Optional<RefreshToken> findByToken(String token);

    RefreshToken createRefreshToken(String username);

    RefreshToken verifyExpiration(RefreshToken token);

    void deleteByUsername(String username);

    void deleteByUserId(Long userId);

    boolean hasActiveSession(String username);
}
