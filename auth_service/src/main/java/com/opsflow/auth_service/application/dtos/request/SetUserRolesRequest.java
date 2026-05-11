package com.opsflow.auth_service.application.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetUserRolesRequest(
        @NotNull @NotEmpty List<@NotBlank String> roleNames
) {}
