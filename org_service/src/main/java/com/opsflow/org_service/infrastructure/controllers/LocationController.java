package com.opsflow.org_service.infrastructure.controllers;

import com.opsflow.org_service.application.dtos.LocationResponse;
import com.opsflow.org_service.application.dtos.MessageResponse;
import com.opsflow.org_service.application.dtos.request.LocationRequest;
import com.opsflow.org_service.domain.ports.in.LocationServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/org/locations")
@Tag(name = "Location Controller", description = "Endpoints for managing locations. Restricted to ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationServicePort locationServicePort;

    public LocationController(LocationServicePort locationServicePort) {
        this.locationServicePort = locationServicePort;
    }

    @Operation(summary = "Create a new location",
            description = "ADMIN: cualquier organizacion. MANAGER: solo en organizaciones a las que pertenece.")
    @PostMapping("/create")
    @PreAuthorize("@orgAccessService.canCreateLocationForOrg(#request.organizationId())")
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        LocationResponse response = locationServicePort.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get all locations", description = "Only accessible by users with ROLE_ADMIN")
    @GetMapping
    @PreAuthorize("@orgAccessService.canReadAllLocations()")
    public ResponseEntity<List<LocationResponse>> findAll() {
        return ResponseEntity.ok(locationServicePort.findAll());
    }

    @Operation(summary = "Get locations by organization ID", description = "Admin: Any. Manager/User: Their own.")
    @GetMapping("/by-org/{orgId}")
    @PreAuthorize("@orgAccessService.canReadAllLocations() or @securityService.isMemberOfOrganization(#orgId)")
    public ResponseEntity<List<LocationResponse>> findByOrganization(@PathVariable Long orgId) {
        return ResponseEntity.ok(locationServicePort.findByOrganizationId(orgId));
    }

    @Operation(summary = "Get location by ID", description = "Admin: Any. Manager/User: Same organization only.")
    @GetMapping("/{id}")
    @PreAuthorize("@orgAccessService.canReadAllLocations() or @orgAccessService.canAccessLocation(#id)")
    public ResponseEntity<LocationResponse> findById(@PathVariable Long id) {
        return locationServicePort.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update a location",
            description = "ADMIN: cualquier sede. MANAGER: solo sedes de organizaciones a las que pertenece.")
    @PutMapping("/{id}")
    @PreAuthorize("@orgAccessService.canUpdateLocation(#id)")
    public ResponseEntity<LocationResponse> update(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return locationServicePort.update(id, request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a location",
            description = "ADMIN: cualquier sede. MANAGER: solo sedes de organizaciones a las que pertenece.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@orgAccessService.canDeleteLocation(#id)")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        if (locationServicePort.delete(id)) {
            return ResponseEntity.ok(new MessageResponse("Sede eliminada exitosamente."));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Sede no encontrada."));
    }
}
