package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.application.dtos.PermissionDto;

import java.util.List;
import java.util.Set;

public interface PermissionService {

    List<PermissionDto> findAll();

    List<Long> findPermissionIdsByRoleId(Long roleId);

    void setRolePermissions(Long roleId, List<Long> permissionIds);

    Set<String> findEffectivePermissionCodesByUsername(String username);
}
