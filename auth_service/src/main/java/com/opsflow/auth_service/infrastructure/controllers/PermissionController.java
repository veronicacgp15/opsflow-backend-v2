package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.PermissionDto;
import com.opsflow.auth_service.application.services.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth/permissions")
@Tag(name = "Permission Controller",
        description = "Catalogo de endpoints/permisos del ecosistema. Restricted to ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "Listar el catalogo completo de permisos",
            description = "Devuelve todos los endpoints registrados (auth, org, document) ordenados por servicio y URL.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PermissionDto>> listAll() {
        return ResponseEntity.ok(permissionService.findAll());
    }
}
