package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.application.dtos.PermissionDto;
import com.opsflow.auth_service.infrastructure.entities.Permission;
import com.opsflow.auth_service.infrastructure.entities.Role;
import com.opsflow.auth_service.infrastructure.repositories.PermissionRepository;
import com.opsflow.auth_service.infrastructure.repositories.RoleRepository;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public PermissionServiceImpl(PermissionRepository permissionRepository,
                                 RoleRepository roleRepository,
                                 UserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDto> findAll() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(Permission::getService)
                        .thenComparing(Permission::getUrlPattern)
                        .thenComparing(Permission::getHttpMethod))
                .map(PermissionDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findPermissionIdsByRoleId(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado: " + roleId));
        return role.getPermissions().stream()
                .map(Permission::getId)
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public void setRolePermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado: " + roleId));

        Set<Permission> resolved = new HashSet<>(
                permissionIds == null ? List.of() : permissionRepository.findAllById(permissionIds)
        );

        role.getPermissions().clear();
        role.getPermissions().addAll(resolved);
        roleRepository.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findEffectivePermissionCodesByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getCode)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }
}
