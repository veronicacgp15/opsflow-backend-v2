package com.opsflow.auth_service.application.dtos;

import java.util.List;

public record UserAdminResponse(
        Long id,
        String username,
        String email,
        String name,
        String lastname,
        Boolean enabled,
        Long organizationId,
        List<String> roles,
        Boolean hasActiveSession
) {}
