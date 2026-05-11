package com.opsflow.org_service.infrastructure.security;

import com.opsflow.common.SecurityService;
import com.opsflow.org_service.domain.ports.in.LocationServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service("orgAccessService")
public class OrgAccessService {

    private static final Logger log = LoggerFactory.getLogger(OrgAccessService.class);

    private final LocationServicePort locationServicePort;
    private final SecurityService securityService;
    private final OrgAuthorizationProperties authorizationProperties;

    public OrgAccessService(
            LocationServicePort locationServicePort,
            SecurityService securityService,
            OrgAuthorizationProperties authorizationProperties
    ) {
        this.locationServicePort = locationServicePort;
        this.securityService = securityService;
        this.authorizationProperties = authorizationProperties;
    }

    public boolean canAccessLocation(Long locationId) {
        return locationServicePort.findById(locationId)
                .map(location -> securityService.isMemberOfOrganization(location.organizationId()))
                .orElse(false);
    }

    public boolean canCreateOrganization() {
        return hasAnyConfiguredRole(authorizationProperties.organizationCreateRoles());
    }

    public boolean canReadAllOrganizations() {
        return hasAnyConfiguredRole(authorizationProperties.organizationReadAllRoles());
    }

    public boolean canDeleteOrganization() {
        return hasAnyConfiguredRole(authorizationProperties.organizationDeleteRoles());
    }

    public boolean canCreateLocation() {
        return hasAnyConfiguredRole(authorizationProperties.locationCreateRoles());
    }

    public boolean canCreateLocationForOrg(Long organizationId) {
        return canCreateLocation()
                && organizationId != null
                && (canReadAllLocations() || securityService.isMemberOfOrganization(organizationId));
    }

    public boolean canReadAllLocations() {
        return hasAnyConfiguredRole(authorizationProperties.locationReadAllRoles());
    }

    public boolean canUpdateLocation() {
        return hasAnyConfiguredRole(authorizationProperties.locationUpdateRoles());
    }

    public boolean canUpdateLocation(Long locationId) {
        return canUpdateLocation()
                && locationId != null
                && (canReadAllLocations() || canAccessLocation(locationId));
    }

    public boolean canDeleteLocation() {
        return hasAnyConfiguredRole(authorizationProperties.locationDeleteRoles());
    }

    public boolean canDeleteLocation(Long locationId) {
        return canDeleteLocation()
                && locationId != null
                && (canReadAllLocations() || canAccessLocation(locationId));
    }

    public boolean canChangeOrganizationStatus(String permissionCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("canChangeOrganizationStatus[{}] DENY: authentication ausente", permissionCode);
            return false;
        }

        Set<String> rawAuthorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        log.info("canChangeOrganizationStatus[{}] user='{}' authorities={}",
                permissionCode, auth.getName(), rawAuthorities);

        if (permissionCode != null && rawAuthorities.contains(permissionCode)) {
            log.debug("canChangeOrganizationStatus[{}] ALLOW por authority de permiso", permissionCode);
            return true;
        }
        if (rawAuthorities.contains("ROLE_ADMIN") || rawAuthorities.contains("ADMIN")) {
            log.debug("canChangeOrganizationStatus[{}] ALLOW por ROLE_ADMIN/ADMIN", permissionCode);
            return true;
        }
        if (hasAnyConfiguredRole(authorizationProperties.organizationDeleteRoles())) {
            log.debug("canChangeOrganizationStatus[{}] ALLOW por organization-delete-roles", permissionCode);
            return true;
        }

        log.warn("canChangeOrganizationStatus[{}] DENY: ninguna via valida (perm='{}', authorities={})",
                permissionCode, permissionCode, rawAuthorities);
        return false;
    }

    private boolean hasAnyConfiguredRole(List<String> configuredRoles) {
        if (configuredRoles == null || configuredRoles.isEmpty()) {
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }

        List<String> userRoles = auth.getAuthorities().stream()
                .map(authority -> normalizeRole(authority.getAuthority()))
                .toList();

        return configuredRoles.stream()
                .map(this::normalizeRole)
                .anyMatch(userRoles::contains);
    }

    private String normalizeRole(String role) {
        String normalized = role.toUpperCase(Locale.ROOT).trim();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}
