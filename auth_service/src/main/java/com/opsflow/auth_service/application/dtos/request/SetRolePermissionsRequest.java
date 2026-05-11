package com.opsflow.auth_service.application.dtos.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetRolePermissionsRequest(
        @NotNull List<@NotNull Long> permissionIds
) {}
