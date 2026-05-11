package com.opsflow.org_service.application.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

public record OrganizationRequest(
    @NotBlank(message = "El nombre es obligatorio")
    String name,

    @NotBlank(message = "El ID fiscal (TaxID) es obligatorio")
    String taxId,

    String address,
    String email,
    String phone,
    Long managerUserId,
    @Min(value = 0, message = "El límite del plan no puede ser negativo")
    Integer planLimit
) {}
