package com.opsflow.org_service.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.org.authorization")
public record OrgAuthorizationProperties(
        List<String> organizationCreateRoles,
        List<String> organizationReadAllRoles,
        List<String> organizationDeleteRoles,
        List<String> locationCreateRoles,
        List<String> locationReadAllRoles,
        List<String> locationUpdateRoles,
        List<String> locationDeleteRoles
) {
}
