package com.opsflow.org_service.application.dtos;

import java.time.LocalDateTime;

public record OrganizationResponse(
    Long id,
    String name,
    String taxId,
    String address,
    String email,
    String phone,
    Boolean active,
    Integer planLimit,
    LocalDateTime createdAt,
    Long createdByUserId,
    String createdByName,
    String createdByLastname,
    String createdByFullName
) {}
