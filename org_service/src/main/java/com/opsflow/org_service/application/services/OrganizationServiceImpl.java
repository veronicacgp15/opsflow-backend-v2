package com.opsflow.org_service.application.services;

import com.opsflow.org_service.application.dtos.UserProfileDto;
import com.opsflow.org_service.application.dtos.request.OrganizationRequest;
import com.opsflow.org_service.application.dtos.OrganizationResponse;
import com.opsflow.org_service.domain.models.OrganizationDomain;
import com.opsflow.org_service.domain.ports.in.OrganizationServicePort;
import com.opsflow.org_service.domain.ports.out.OrganizationRepositoryPort;
import com.opsflow.org_service.infrastructure.adapters.external.AuthServiceClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrganizationServiceImpl implements OrganizationServicePort {

    private final OrganizationRepositoryPort organizationRepositoryPort;
    private final UserProfileResolver userProfileResolver;
    private final AuthServiceClient authServiceClient;

    public OrganizationServiceImpl(OrganizationRepositoryPort organizationRepositoryPort,
                                   UserProfileResolver userProfileResolver,
                                   AuthServiceClient authServiceClient) {
        this.organizationRepositoryPort = organizationRepositoryPort;
        this.userProfileResolver = userProfileResolver;
        this.authServiceClient = authServiceClient;
    }

    @Override
    @Transactional
    public OrganizationResponse create(OrganizationRequest request, Long createdByUserId) {
        if (request.managerUserId() == null) {
            throw new IllegalArgumentException("La creacion de la organizacion requiere asignar un manager.");
        }

        OrganizationDomain domain = new OrganizationDomain();
        domain.setName(request.name());
        domain.setTaxId(request.taxId());
        domain.setAddress(request.address());
        domain.setEmail(request.email());
        domain.setPhone(request.phone());
        domain.setActive(true);
        domain.setPlanLimit(request.planLimit() != null ? request.planLimit() : 10);
        domain.setCreatedAt(LocalDateTime.now());
        domain.setCreatedByUserId(createdByUserId);

        OrganizationDomain savedDomain = organizationRepositoryPort.save(domain);
        assignInitialManagerOrRollback(savedDomain.getId(), request.managerUserId());
        return enrichSingle(savedDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationResponse> findById(Long id) {
        return organizationRepositoryPort.findById(id).map(this::enrichSingle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> findAll() {
        return enrichList(organizationRepositoryPort.findAll());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        organizationRepositoryPort.deleteById(id);
    }

    @Override
    @Transactional
    public Optional<OrganizationResponse> update(Long id, OrganizationRequest request) {
        return organizationRepositoryPort.findById(id).map(domain -> {
            domain.setName(request.name());
            domain.setTaxId(request.taxId());
            domain.setAddress(request.address());
            domain.setEmail(request.email());
            domain.setPhone(request.phone());

            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin && request.planLimit() != null) {
                domain.setPlanLimit(request.planLimit());
            }

            OrganizationDomain updatedDomain = organizationRepositoryPort.save(domain);
            return enrichSingle(updatedDomain);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> findByCreatedByUserId(Long createdByUserId) {
        return enrichList(organizationRepositoryPort.findByCreatedByUserId(createdByUserId));
    }


    @Override
    @Transactional
    public Optional<OrganizationResponse> setActive(Long id, boolean active) {
        return organizationRepositoryPort.findById(id).map(domain -> {
            if (Objects.equals(domain.getActive(), active)) {
                return enrichSingle(domain);
            }
            domain.setActive(active);
            return enrichSingle(organizationRepositoryPort.save(domain));
        });
    }

    private OrganizationResponse enrichSingle(OrganizationDomain domain) {
        if (domain == null) {
            return null;
        }
        Long creatorId = domain.getCreatedByUserId();
        if (creatorId == null) {
            return mapToResponse(domain, null, null);
        }
        Map<Long, UserProfileDto> profiles =
                userProfileResolver.resolveByIds(Collections.singleton(creatorId));
        UserProfileDto p = profiles.get(creatorId);
        return mapToResponse(domain,
                p != null ? p.name() : null,
                p != null ? p.lastname() : null);
    }

    private List<OrganizationResponse> enrichList(List<OrganizationDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        List<Long> creatorIds = domains.stream()
                .map(OrganizationDomain::getCreatedByUserId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, UserProfileDto> profiles = userProfileResolver.resolveByIds(creatorIds);
        return domains.stream().map(domain -> {
            UserProfileDto p = domain.getCreatedByUserId() != null
                    ? profiles.get(domain.getCreatedByUserId())
                    : null;
            return mapToResponse(domain,
                    p != null ? p.name() : null,
                    p != null ? p.lastname() : null);
        }).toList();
    }

    private OrganizationResponse mapToResponse(OrganizationDomain domain,
                                               String createdByName,
                                               String createdByLastname) {
        return new OrganizationResponse(
                domain.getId(),
                domain.getName(),
                domain.getTaxId(),
                domain.getAddress(),
                domain.getEmail(),
                domain.getPhone(),
                domain.getActive(),
                domain.getPlanLimit(),
                domain.getCreatedAt(),
                domain.getCreatedByUserId(),
                createdByName,
                createdByLastname,
                buildFullName(createdByName, createdByLastname)
        );
    }

    private String buildFullName(String name, String lastname) {
        String full = java.util.stream.Stream.of(name, lastname)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        return full.isBlank() ? null : full;
    }

    private void assignInitialManagerOrRollback(Long organizationId, Long managerUserId) {
        try {
            authServiceClient.assignOrganizationManager(organizationId, managerUserId);
        } catch (RuntimeException ex) {
            String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? " Detalle: " + ex.getMessage()
                    : "";
            throw new IllegalStateException(
                    "No se pudo asignar el manager inicial durante la creacion de la organizacion." + detail, ex);
        }
    }
}
