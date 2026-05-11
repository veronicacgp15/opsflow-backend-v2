package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.UserProfileDto;
import com.opsflow.auth_service.application.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/users/profiles")
@Tag(name = "User Profiles", description = "Resolucion de perfiles publicos por id (uso interno).")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class UserProfileController {

    private static final int MAX_BATCH_SIZE = 200;

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Resolver perfiles por id en lote",
            description = "Devuelve {id, username, name, lastname} para los ids dados. " +
                    "Los ids inexistentes se omiten silenciosamente. Tope: " + MAX_BATCH_SIZE + " ids.")
    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserProfileDto>> batch(@RequestBody @NotEmpty List<Long> ids) {
        List<Long> uniqueIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(MAX_BATCH_SIZE)
                .toList();

        if (uniqueIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Set<Long> requested = Set.copyOf(uniqueIds);

        List<UserProfileDto> profiles = uniqueIds.stream()
                .map(userService::findById)
                .flatMap(java.util.Optional::stream)
                .filter(u -> requested.contains(u.getId()))
                .map(u -> new UserProfileDto(
                        u.getId(),
                        u.getUsername(),
                        u.getName(),
                        u.getLastname()
                ))
                .toList();

        return ResponseEntity.ok(profiles);
    }
}
