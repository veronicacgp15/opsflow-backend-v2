package com.opsflow.org_service.application.dtos;


public record UserProfileDto(
        Long id,
        String username,
        String name,
        String lastname
) {}
