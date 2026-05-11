package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.UserAdminResponse;
import com.opsflow.auth_service.application.dtos.MessageResponse;
import com.opsflow.auth_service.application.dtos.request.ChangePasswordRequest;
import com.opsflow.auth_service.application.dtos.request.CreateUserRequest;
import com.opsflow.auth_service.application.dtos.request.SetUserRolesRequest;
import com.opsflow.auth_service.application.dtos.request.UpdateUserRequest;
import com.opsflow.auth_service.application.services.RefreshTokenService;
import com.opsflow.auth_service.application.services.UserService;
import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.common.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.opsflow.auth_service.domain.constants.AuthConstants.ROLE_MANAGER;
import static com.opsflow.auth_service.domain.constants.AuthConstants.ROLE_USER;


@RestController
@RequestMapping("/users")
@Tag(name = "Admin users", description = "Gestion de usuarios. ADMIN: completo. MANAGER: alcance a su organizacion.")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AdminUserController(UserService userService,
                               RefreshTokenService refreshTokenService,
                               PasswordEncoder passwordEncoder,
                               JwtUtils jwtUtils) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Operation(summary = "Listar todos los usuarios", description = "Solo ADMIN.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserAdminResponse>> listUsers() {
        return ResponseEntity.ok(
                userService.findAll().stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Listar usuarios de mi organizacion",
            description = "MANAGER lista los usuarios cuya organizacion coincide con la suya. " +
                    "ADMIN puede usarlo tambien (devuelve los de la org incrustada en su JWT).")
    @GetMapping("/my-organization")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<UserAdminResponse>> listMyOrganization(Authentication authentication) {
        Long orgId = resolveCallerOrganizationId(authentication);
        if (orgId == null) {
            return ResponseEntity.ok(List.of());
        }
        List<UserAdminResponse> users = userService.findByOrganizationId(orgId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Listar usuarios de una organizacion concreta",
            description = "ADMIN: cualquier organizacion. MANAGER: solo su propia organizacion.")
    @GetMapping("/by-organization/{orgId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<UserAdminResponse>> listUsersByOrganization(@PathVariable Long orgId,
                                                                           Authentication authentication) {
        boolean isAdmin = jwtUtils.hasRole(authentication, "ADMIN");
        Long callerOrgId = resolveCallerOrganizationId(authentication);

        if (!isAdmin && (callerOrgId == null || !callerOrgId.equals(orgId))) {
            throw new AccessDeniedException(
                    "Solo puedes consultar usuarios de tu propia organizacion.");
        }

        List<UserAdminResponse> users = userService.findByOrganizationId(orgId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Obtener usuario por id", description = "Solo ADMIN.")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminResponse> getUser(@PathVariable Long id) {
        return userService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear/invitar un usuario",
            description = "ADMIN: cualquier rol y org. MANAGER: solo ROLE_USER en su propia org.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserAdminResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                        Authentication authentication) {

        if (userService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        boolean isAdmin = jwtUtils.hasRole(authentication, "ADMIN");
        Long callerOrgId = jwtUtils.getOrganizationIdFromAuthentication(authentication);

        Long targetOrgId;
        if (isAdmin) {
            targetOrgId = request.organizationId() != null ? request.organizationId() : callerOrgId;
        } else {
            if (callerOrgId == null) {
                throw new AccessDeniedException("MANAGER sin organizacion asociada en el JWT.");
            }
            targetOrgId = callerOrgId;
        }

        List<String> requestedRoles = request.roles() == null || request.roles().isEmpty()
                ? List.of(ROLE_USER)
                : request.roles().stream().map(this::normalizeRole).distinct().toList();

        if (!isAdmin) {
            boolean onlyUser = requestedRoles.stream().allMatch(ROLE_USER::equals);
            if (!onlyUser) {
                throw new AccessDeniedException("MANAGER solo puede crear usuarios con rol ROLE_USER.");
            }
        }

        UserDomain newUser = new UserDomain();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setName(request.name());
        newUser.setLastname(request.lastname());
        newUser.setPassword(request.password());
        newUser.setOrganizationId(targetOrgId);
        newUser.setRoles(requestedRoles);
        if (isAdmin && requestedRoles.contains(ROLE_MANAGER)) {
            newUser.setEnabled(true);
        }

        UserDomain saved = userService.save(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @Operation(summary = "Actualizar datos basicos de un usuario",
            description = "Solo ADMIN. Edita email/nombre/apellido/organizacion. " +
                    "Para password usar /users/change-password (propio) o reset-password. " +
                    "Para roles usar PATCH /users/{id}/roles. Para enabled usar /activate o /deactivate.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminResponse> updateUser(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateUserRequest request) {
        return userService.findById(id).map(existing -> {
            if (request.email() != null) existing.setEmail(request.email());
            if (request.name() != null) existing.setName(request.name());
            if (request.lastname() != null) existing.setLastname(request.lastname());
            if (request.organizationId() != null) existing.setOrganizationId(request.organizationId());
            return userService.update(id, existing)
                    .map(this::toResponse)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Asignar roles a un usuario (PATCH)",
            description = "Solo ADMIN. Reemplaza la lista completa de roles. " +
                    "Existe tambien PUT /auth/roles/users/{userId}/roles con el mismo efecto.")
    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> updateUserRoles(@PathVariable Long id,
                                                           @Valid @RequestBody SetUserRolesRequest request) {
        List<String> normalized = request.roleNames().stream()
                .map(this::normalizeRole)
                .distinct()
                .toList();
        return userService.updateRoles(id, normalized)
                .map(u -> ResponseEntity.ok(new MessageResponse("Roles del usuario actualizados.")))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Asignar o cambiar el manager activo de una organizacion",
            description = "Solo ADMIN. El usuario objetivo debe pertenecer a la organizacion y estar activo. " +
                    "Acepta PUT y PATCH para compatibilidad con clientes internos y frontend.")
    @RequestMapping(value = "/organizations/{orgId}/manager/{userId}",
            method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminResponse> assignOrganizationManager(@PathVariable Long orgId,
                                                                       @PathVariable Long userId) {
        return userService.assignOrganizationManager(orgId, userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cambiar mi propia contrasena",
            description = "Cualquier autenticado. Verifica la contrasena actual antes de aplicar el cambio.")
    @PatchMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                            Authentication authentication) {
        var current = userService.findByUsername(authentication.getName()).orElseThrow();
        if (!passwordEncoder.matches(request.currentPassword(), current.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("La contrasena actual no es correcta."));
        }
        userService.changePassword(current.getId(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Contrasena actualizada correctamente."));
    }

    @Operation(summary = "Desactivar cuenta de usuario", description = "Solo ADMIN.")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deactivate(@PathVariable Long id) {
        userService.deactivateAccount(id);
        return ResponseEntity.ok(new MessageResponse("Usuario desactivado."));
    }

    @Operation(summary = "Activar cuenta de usuario", description = "Solo ADMIN.")
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> activate(@PathVariable Long id) {
        userService.activateAccount(id);
        return ResponseEntity.ok(new MessageResponse("Usuario activado."));
    }

    @Operation(summary = "Revocar sesion (elimina refresh token del usuario)", description = "Solo ADMIN.")
    @PostMapping("/{id}/revoke-session")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> revokeSession(@PathVariable Long id) {
        refreshTokenService.deleteByUserId(id);
        return ResponseEntity.ok(new MessageResponse("Sesion revocada (refresh token eliminado)."));
    }

    @Operation(summary = "Generar hash BCrypt de una contrasena (herramienta admin)", description = "Solo ADMIN.")
    @GetMapping("/tools/password-hash")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> passwordHash(@RequestParam String password) {
        return ResponseEntity.ok(passwordEncoder.encode(password));
    }

    private String normalizeRole(String raw) {
        if (raw == null) return ROLE_USER;
        String t = raw.trim().toUpperCase();
        return t.startsWith("ROLE_") ? t : "ROLE_" + t;
    }

    private Long resolveCallerOrganizationId(Authentication authentication) {
        Long fromJwt = jwtUtils.getOrganizationIdFromAuthentication(authentication);
        if (fromJwt != null) {
            return fromJwt;
        }
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(UserDomain::getOrganizationId)
                .orElse(null);
    }

    private UserAdminResponse toResponse(UserDomain u) {
        return new UserAdminResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getName(),
                u.getLastname(),
                u.getEnabled(),
                u.getOrganizationId(),
                u.getRoles() != null ? List.copyOf(u.getRoles()) : List.of(),
                refreshTokenService.hasActiveSession(u.getUsername())
        );
    }
}
