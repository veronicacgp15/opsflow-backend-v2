package com.opsflow.auth_service.infrastructure.repositories;

import com.opsflow.auth_service.infrastructure.entities.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser_Id(Long userId);
}
