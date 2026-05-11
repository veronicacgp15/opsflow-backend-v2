package com.opsflow.auth_service.domain.ports.out;

import com.opsflow.auth_service.domain.models.UserDomain;
import java.util.Optional;
import java.util.List;

public interface UserRepositoryPort {
    Optional<UserDomain> findByUsername(String username);
    Optional<UserDomain> findByEmail(String email);
    Optional<UserDomain> findById(Long id);
    UserDomain save(UserDomain userDomain);
    List<UserDomain> findAll();
    void deleteById(Long id);
    Optional<UserDomain> update(Long id, UserDomain userDomain);
}
