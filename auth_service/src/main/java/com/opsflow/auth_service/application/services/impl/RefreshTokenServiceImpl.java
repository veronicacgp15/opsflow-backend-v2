package com.opsflow.auth_service.application.services.impl;

import com.opsflow.auth_service.application.services.RefreshTokenService;
import com.opsflow.auth_service.infrastructure.entities.RefreshToken;
import com.opsflow.auth_service.infrastructure.repositories.RefreshTokenRepository;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${app.jwt.refreshExpiration}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findById(token);
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        refreshTokenRepository.findByUsername(username).ifPresent(refreshTokenRepository::delete);

        var refreshToken = new RefreshToken();
        refreshToken.setUsername(username);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setId(UUID.randomUUID().toString());
        refreshToken.setTtl(refreshTokenDurationMs / 1000);

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) throws RuntimeException {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Override
    @Transactional
    public void deleteByUsername(String username) {
        refreshTokenRepository.findByUsername(username).ifPresent(refreshTokenRepository::delete);
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        userRepository.findById(userId)
                .ifPresent(u -> deleteByUsername(u.getUsername()));
    }

    @Override
    public boolean hasActiveSession(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return refreshTokenRepository.findByUsername(username).isPresent();
    }
}
