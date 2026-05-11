package com.opsflow.auth_service.infrastructure.repositories;

import com.opsflow.auth_service.infrastructure.entities.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
