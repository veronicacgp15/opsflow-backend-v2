package com.opsflow.auth_service.application.dtos;

import com.opsflow.auth_service.infrastructure.entities.Permission;


public record PermissionDto(
        Long id,
        String code,
        String service,
        String httpMethod,
        String urlPattern,
        String description
) {

    public static PermissionDto from(Permission p) {
        return new PermissionDto(
                p.getId(),
                p.getCode(),
                p.getService(),
                p.getHttpMethod(),
                p.getUrlPattern(),
                p.getDescription()
        );
    }
}
