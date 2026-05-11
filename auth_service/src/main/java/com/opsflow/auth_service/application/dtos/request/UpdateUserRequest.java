package com.opsflow.auth_service.application.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 50) @Email String email,
        @Size(min = 2, max = 50) String name,
        @Size(min = 2, max = 50) String lastname,
        Long organizationId
) {}
