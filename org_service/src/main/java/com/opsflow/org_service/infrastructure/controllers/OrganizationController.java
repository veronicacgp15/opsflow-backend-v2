package com.opsflow.org_service.infrastructure.controllers;

import com.opsflow.common.JwtUtils;
import com.opsflow.org_service.application.dtos.request.OrganizationRequest;
import com.opsflow.org_service.application.dtos.OrganizationResponse;
import com.opsflow.org_service.application.dtos.MessageResponse;
import com.opsflow.org_service.domain.ports.in.OrganizationServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/org")
@Tag(name = "Organization Controller", description = "Endpoints for managing organizations. Restricted to ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationServicePort organizationServicePort;
    private final JwtUtils jwtUtils;

    public OrganizationController(OrganizationServicePort organizationServicePort, JwtUtils jwtUtils) {
        this.organizationServicePort = organizationServicePort;
        this.jwtUtils = jwtUtils;
    }

    @Operation(summary = "Create a new organization", description = "Only accessible by users with ROLE_ADMIN")
    @PostMapping("/create")
    @PreAuthorize("@orgAccessService.canCreateOrganization()")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request,
                                                      Authentication authentication) {
        if (request.managerUserId() == null) {
            throw new IllegalArgumentException("Debes seleccionar un manager en el formulario inicial.");
        }
        Long creatorId = jwtUtils.getUserIdFromAuthentication(authentication);
        OrganizationResponse response = organizationServicePort.create(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Organizaciones creadas por el usuario autenticado", description = "Filtra por createdByUserId del JWT")
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponse>> myOrganizations(Authentication authentication) {
        Long userId = jwtUtils.getUserIdFromAuthentication(authentication);
        if (userId == null && !jwtUtils.hasRole(authentication, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (jwtUtils.hasRole(authentication, "ADMIN")) {
            return ResponseEntity.ok(organizationServicePort.findByCreatedByUserId(userId));
        }

        Long orgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);
        if (orgId == null) {
            return ResponseEntity.ok(List.of());
        }
        return organizationServicePort.findById(orgId)
                .map(response -> ResponseEntity.ok(List.of(response)))
                .orElseGet(() -> ResponseEntity.ok(List.of()));
    }

    @Operation(summary = "Get all organizations", description = "Only accessible by users with ROLE_ADMIN")
    @GetMapping
    @PreAuthorize("@orgAccessService.canReadAllOrganizations()")
    public ResponseEntity<List<OrganizationResponse>> findAll() {
        List<OrganizationResponse> response = organizationServicePort.findAll();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get organization by ID", description = "Admin: Any. Manager/User: Their own.")
    @GetMapping("/{id}")
    @PreAuthorize("@orgAccessService.canReadAllOrganizations() or @securityService.isMemberOfOrganization(#id)")
    public ResponseEntity<OrganizationResponse> findById(@PathVariable Long id) {
        return organizationServicePort.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update an organization",
            description = "Admin: cualquier organizacion. Manager: solo la suya. " +
                    "USER NO puede editar la organizacion (requisito de negocio).")
    @PutMapping("/{id}")
    @PreAuthorize("@orgAccessService.canReadAllOrganizations() " +
            "or (hasRole('MANAGER') and @securityService.isMemberOfOrganization(#id))")
    public ResponseEntity<OrganizationResponse> update(@PathVariable Long id, @Valid @RequestBody OrganizationRequest request) {
        // En un escenario real, aqui podrias filtrar que el Manager no cambie el planLimit
        return organizationServicePort.update(id, request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an organization", description = "Only accessible by users with ROLE_ADMIN")
    @DeleteMapping("/{id}")
    @PreAuthorize("@orgAccessService.canDeleteOrganization()")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        organizationServicePort.delete(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new MessageResponse("Organización con ID " + id + " eliminada exitosamente."));
    }

    @Operation(summary = "Activar una organizacion", description = "Marca el flag active=true. Idempotente.")
    @PatchMapping("/{id}/activate")
    @PreAuthorize("@orgAccessService.canChangeOrganizationStatus('ORG_ACTIVATE')")
    public ResponseEntity<OrganizationResponse> activate(@PathVariable Long id) {
        return organizationServicePort.setActive(id, true)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Desactivar una organizacion", description = "Marca el flag active=false. Idempotente.")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@orgAccessService.canChangeOrganizationStatus('ORG_DEACTIVATE')")
    public ResponseEntity<OrganizationResponse> deactivate(@PathVariable Long id) {
        return organizationServicePort.setActive(id, false)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
