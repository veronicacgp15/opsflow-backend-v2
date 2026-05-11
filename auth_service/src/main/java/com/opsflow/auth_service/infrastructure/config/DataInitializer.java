package com.opsflow.auth_service.infrastructure.config;

import com.opsflow.auth_service.application.services.UserService;
import com.opsflow.auth_service.domain.constants.SystemPermission;
import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.auth_service.infrastructure.entities.Permission;
import com.opsflow.auth_service.infrastructure.entities.Role;
import com.opsflow.auth_service.infrastructure.repositories.PermissionRepository;
import com.opsflow.auth_service.infrastructure.repositories.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.opsflow.auth_service.domain.constants.AuthConstants.*;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public DataInitializer(UserService userService,
                           RoleRepository roleRepository,
                           PermissionRepository permissionRepository) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        Stream.of(ROLE_ADMIN, ROLE_MANAGER, ROLE_USER).forEach(this::createRoleIfNotFound);

        syncPermissionsCatalog();
        seedDefaultRolePermissionsIfEmpty();

        createDefaultUser("admin", "admin@opsflow.com", "Admin", "Admin", ROLE_ADMIN, ROLE_USER);
        createDefaultUser("manager", "manager@opsflow.com", "Manager", "Manager", ROLE_MANAGER, ROLE_USER);
        createDefaultUser("user", "user@opsflow.com", "User", "User", ROLE_USER);

        ensureSeedUserHasRoles("admin", ROLE_ADMIN);
        ensureSeedUserHasRoles("manager", ROLE_MANAGER);
        ensureSeedUserHasRoles("user", ROLE_USER);
    }


    @Transactional
    void syncPermissionsCatalog() {
        Set<String> validCodes = new LinkedHashSet<>(Arrays.stream(SystemPermission.values())
                .peek(this::upsertPermissionFromSeed)
                .map(SystemPermission::code)
                .toList());

        permissionRepository.findAll().stream()
                .filter(existing -> !validCodes.contains(existing.getCode()))
                .forEach(this::deleteObsoletePermission);
    }

    @Transactional
    void seedDefaultRolePermissionsIfEmpty() {
        Map<String, Set<SystemPermission>> matrix = defaultPermissionMatrix();
        Map<String, Permission> byCode = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, permission -> permission, (a, b) -> a, HashMap::new));

        matrix.forEach((roleName, expected) -> roleRepository.findByName(roleName).ifPresent(role -> {
            boolean isAdmin = ROLE_ADMIN.equals(roleName);

            if (!isAdmin && !role.getPermissions().isEmpty()) {
                return;
            }

            Set<Permission> resolved = new LinkedHashSet<>(expected.stream()
                    .map(SystemPermission::code)
                    .map(byCode::get)
                    .filter(java.util.Objects::nonNull)
                    .toList());

            if (isAdmin) {
                Set<Long> currentIds = toPermissionIds(role.getPermissions());
                Set<Long> expectedIds = toPermissionIds(resolved);
                if (currentIds.equals(expectedIds)) {
                    return;
                }
                role.getPermissions().clear();
                role.getPermissions().addAll(resolved);
                roleRepository.save(role);
                System.out.println("Permisos de " + roleName + " sincronizados al catalogo completo: "
                        + resolved.size());
            } else {
                role.getPermissions().addAll(resolved);
                roleRepository.save(role);
                System.out.println("Permisos por defecto aplicados a " + roleName + ": " + resolved.size());
            }
        }));
    }


    private Map<String, Set<SystemPermission>> defaultPermissionMatrix() {
        Map<String, Set<SystemPermission>> matrix = new HashMap<>();

        matrix.put(ROLE_ADMIN, EnumSet.allOf(SystemPermission.class));


        matrix.put(ROLE_MANAGER, EnumSet.of(
                // -- Auth --
                SystemPermission.AUTH_LOGOUT,
                SystemPermission.USERS_CREATE,           // invitar usuarios (manager solo a su org)
                SystemPermission.USERS_MY_ORGANIZATION,  // listar usuarios de su org
                SystemPermission.USERS_CHANGE_PASSWORD,  // cambiar su propia password
                SystemPermission.USERS_PROFILES_BATCH,   // resolver nombres en tablas (helper UI)
                // -- Org --
                SystemPermission.ORG_GET,
                SystemPermission.ORG_UPDATE,
                SystemPermission.LOCATION_CREATE,
                SystemPermission.LOCATION_BY_ORG,
                SystemPermission.LOCATION_GET,
                SystemPermission.LOCATION_UPDATE,
                SystemPermission.LOCATION_DELETE,
                // -- Documents --
                SystemPermission.DOC_CREATE,
                SystemPermission.DOC_LIST,
                SystemPermission.DOC_GET,
                SystemPermission.DOC_UPDATE,
                SystemPermission.DOC_DELETE,
                SystemPermission.DOC_ADD_VERSION,
                SystemPermission.DOC_TYPES_LIST,
                SystemPermission.DOC_DOWNLOAD

        ));

        matrix.put(ROLE_USER, EnumSet.of(
                // -- Auth --
                SystemPermission.AUTH_LOGOUT,
                SystemPermission.USERS_CHANGE_PASSWORD,  // cambiar su propia password
                SystemPermission.USERS_PROFILES_BATCH,   // helper para "Subido por" / "Creado por"
                // -- Org --
                SystemPermission.ORG_GET,
                SystemPermission.LOCATION_BY_ORG,
                SystemPermission.LOCATION_GET,
                // -- Documents --
                SystemPermission.DOC_CREATE,
                SystemPermission.DOC_LIST,
                SystemPermission.DOC_GET,
                SystemPermission.DOC_UPDATE,
                SystemPermission.DOC_ADD_VERSION,
                SystemPermission.DOC_TYPES_LIST,
                SystemPermission.DOC_DOWNLOAD
        ));

        return matrix;
    }

    private void ensureSeedUserHasRoles(String username, String... expectedRoles) {
        userService.findByUsername(username).ifPresent(user -> {
            Set<String> merged = new LinkedHashSet<>(Stream.concat(
                            safeRoles(user).stream(),
                            Arrays.stream(expectedRoles))
                    .toList());
            boolean changed = merged.size() != safeRoles(user).size();
            if (changed) {
                user.setRoles(new ArrayList<>(merged));
                userService.update(user.getId(), user);
                System.out.println("Roles de semilla sincronizados para " + username + ": " + merged);
            }
        });
    }

    private void createDefaultUser(String username, String email, String name, String lastname,
                                   String... roles) {
        userService.findByUsername(username).ifPresentOrElse(
                user -> enableUserIfNeeded(username, user),
                () -> {
                    UserDomain newUser = new UserDomain();
                    newUser.setUsername(username);
                    newUser.setPassword("123456");
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setLastname(lastname);
                    newUser.setEnabled(true);
                    newUser.setOrganizationId(1L);

                    newUser.setRoles(Arrays.asList(roles));

                    var createdUser = userService.save(newUser);
                    createdUser.setEnabled(true);
                    userService.update(createdUser.getId(), createdUser);
                    System.out.println("Usuario " + username + " creado con roles: " + Arrays.toString(roles));
                }
        );
    }

    private void createRoleIfNotFound(String roleName) {
        roleRepository.findByName(roleName).orElseGet(() -> createRole(roleName));
    }

    private void upsertPermissionFromSeed(SystemPermission seed) {
        permissionRepository.findByCode(seed.code()).ifPresentOrElse(
                existing -> saveIfDirty(existing, seed),
                () -> createPermission(seed));
    }

    private void saveIfDirty(Permission existing, SystemPermission seed) {
        boolean dirty = applySeedValues(existing, seed);
        if (dirty) {
            permissionRepository.save(existing);
        }
    }

    private boolean applySeedValues(Permission existing, SystemPermission seed) {
        boolean serviceChanged = updateField(seed.service(), existing.getService(), existing::setService);
        boolean methodChanged = updateField(seed.httpMethod(), existing.getHttpMethod(), existing::setHttpMethod);
        boolean patternChanged = updateField(seed.urlPattern(), existing.getUrlPattern(), existing::setUrlPattern);
        boolean descriptionChanged = updateField(seed.description(), existing.getDescription(), existing::setDescription);
        return serviceChanged || methodChanged || patternChanged || descriptionChanged;
    }

    private <T> boolean updateField(T expected, T current, java.util.function.Consumer<T> setter) {
        if (java.util.Objects.equals(expected, current)) {
            return false;
        }
        setter.accept(expected);
        return true;
    }

    private void createPermission(SystemPermission seed) {
        Permission permission = new Permission(
                seed.code(),
                seed.service(),
                seed.httpMethod(),
                seed.urlPattern(),
                seed.description()
        );
        permissionRepository.save(permission);
        System.out.println("Permiso semilla creado: " + seed.code());
    }

    private void deleteObsoletePermission(Permission permission) {
        roleRepository.findAll().forEach(role -> detachPermissionFromRole(role, permission));
        permissionRepository.delete(permission);
        System.out.println("Permiso obsoleto eliminado del catalogo: " + permission.getCode());
    }

    private void detachPermissionFromRole(Role role, Permission permission) {
        boolean removed = role.getPermissions().removeIf(p -> p.getId().equals(permission.getId()));
        if (removed) {
            roleRepository.save(role);
        }
    }

    private Set<Long> toPermissionIds(Set<Permission> permissions) {
        return new LinkedHashSet<>(permissions.stream()
                .map(Permission::getId)
                .toList());
    }

    private List<String> safeRoles(UserDomain user) {
        return user.getRoles() != null ? user.getRoles() : List.of();
    }

    private void enableUserIfNeeded(String username, UserDomain user) {
        if (Boolean.TRUE.equals(user.getEnabled())) {
            return;
        }
        user.setEnabled(true);
        userService.update(user.getId(), user);
        System.out.println("Usuario " + username + " habilitado para inicio de sesion.");
    }

    private Role createRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Role saved = roleRepository.save(role);
        System.out.println("Rol " + roleName + " creado.");
        return saved;
    }
}
