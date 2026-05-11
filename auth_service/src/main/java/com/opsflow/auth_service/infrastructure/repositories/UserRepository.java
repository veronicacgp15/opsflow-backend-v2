package com.opsflow.auth_service.infrastructure.repositories;

import com.opsflow.auth_service.infrastructure.entities.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
}
