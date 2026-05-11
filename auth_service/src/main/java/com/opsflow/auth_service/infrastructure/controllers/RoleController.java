package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.MessageResponse;
import com.opsflow.auth_service.application.dtos.request.ChangeRoleRequest;
import com.opsflow.auth_service.application.dtos.request.CreateRoleRequest;
import com.opsflow.auth_service.application.dtos.request.SetRolePermissionsRequest;
import com.opsflow.auth_service.application.dtos.request.SetUserRolesRequest;
import com.opsflow.auth_service.application.services.PermissionService;
import com.opsflow.auth_service.application.services.RoleService;
import com.opsflow.auth_service.application.services.UserService;
import com.opsflow.auth_service.infrastructure.entities.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/roles")
@Tag(name = "Role Controller", description = "Endpoints for managing roles and user role assignments. Restricted to ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;
    private final UserService userService;
    private final PermissionService permissionService;

    public RoleController(RoleService roleService,
                          UserService userService,
                          PermissionService permissionService) {
        this.roleService = roleService;
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @Operation(summary = "Create a new role", description = "Only accessible by users with ROLE_ADMIN")
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role savedRole = roleService.save(request.name());
        return ResponseEntity.ok(savedRole);
    }

    @Operation(summary = "Update an existing role", description = "Only accessible by users with ROLE_ADMIN")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> updateRole(@PathVariable Long id, @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request.name()));
    }

    @Operation(summary = "Delete a role", description = "Only accessible by users with ROLE_ADMIN")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(new MessageResponse("Rol eliminado exitosamente."));
    }

    @Operation(summary = "Change a user's role",
            description = "Sustituye la lista completa de roles del usuario por un solo rol. " +
                    "Para conservar varios roles (p. ej. ADMIN + USER) hay que ampliar este endpoint o actualizar la tabla users_to_roles manualmente. " +
                    "Solo accesible con ROLE_ADMIN.")
    @PutMapping("/users/{userId}/change-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> changeUserRole(@PathVariable Long userId,
                                                          @Valid @RequestBody
                                                          ChangeRoleRequest request) {
        String normalized = normalizeRoleName(request.roleName());
        return userService.updateRoles(userId, List.of(normalized))
                .map(user -> ResponseEntity.ok(new MessageResponse(
                        "Rol de usuario " + userId + " actualizado a " + normalized)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Asignar roles completos al usuario", description = "Reemplaza todos los roles por la lista enviada. Solo ROLE_ADMIN.")
    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> setUserRoles(@PathVariable Long userId,
                                                        @Valid @RequestBody SetUserRolesRequest request) {
        List<String> normalized = request.roleNames().stream()
                .map(this::normalizeRoleName)
                .distinct()
                .toList();
        return userService.updateRoles(userId, normalized)
                .map(user -> ResponseEntity.ok(new MessageResponse("Roles del usuario actualizados.")))
                .orElse(ResponseEntity.notFound().build());
    }

    private String normalizeRoleName(String raw) {
        String t = raw.trim().toUpperCase();
        return t.startsWith("ROLE_") ? t : "ROLE_" + t;
    }

    @Operation(summary = "Get all roles", description = "Only accessible by users with ROLE_ADMIN")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(roleService.findAll());
    }

    @Operation(summary = "Get role by ID", description = "Only accessible by users with ROLE_ADMIN")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        return roleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get role permissions",
            description = "Devuelve los IDs de permisos asignados al rol indicado. Solo ROLE_ADMIN.")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Long>> getRolePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.findPermissionIdsByRoleId(id));
    }

    @Operation(summary = "Set role permissions",
            description = "Reemplaza la lista completa de permisos del rol por la enviada. Solo ROLE_ADMIN.")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> setRolePermissions(
            @PathVariable Long id,
            @Valid @RequestBody SetRolePermissionsRequest request) {
        permissionService.setRolePermissions(id, request.permissionIds());
        return ResponseEntity.ok(new MessageResponse("Permisos del rol actualizados."));
    }
}
