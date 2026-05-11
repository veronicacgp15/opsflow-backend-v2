package com.opsflow.org_service.infrastructure.adapters;

import com.opsflow.org_service.domain.models.OrganizationDomain;
import com.opsflow.org_service.domain.ports.out.OrganizationRepositoryPort;
import com.opsflow.org_service.infrastructure.entities.Organization;
import com.opsflow.org_service.infrastructure.mappers.OrganizationMapper;
import com.opsflow.org_service.infrastructure.repositories.OrganizationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrganizationPersistenceAdapter implements OrganizationRepositoryPort {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    public OrganizationPersistenceAdapter(OrganizationRepository organizationRepository, OrganizationMapper organizationMapper) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
    }

    @Override
    public OrganizationDomain save(OrganizationDomain organization) {
        Organization entity = organizationMapper.toEntity(organization);

        Organization savedEntity = organizationRepository.save(entity);

        return organizationMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<OrganizationDomain> findById(Long id) {
        return organizationRepository.findById(id)
                .map(organizationMapper::toDomain);
    }

    @Override
    public List<OrganizationDomain> findAll() {
        return organizationRepository.findAll().stream()
                .map(organizationMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        organizationRepository.deleteById(id);
    }

    @Override
    public Optional<OrganizationDomain> findByTaxId(String taxId) {
        return organizationRepository.findByTaxId(taxId)
                .map(organizationMapper::toDomain);
    }

    @Override
    public List<OrganizationDomain> findByCreatedByUserId(Long createdByUserId) {
        return organizationRepository.findByCreatedByUserId(createdByUserId).stream()
                .map(organizationMapper::toDomain)
                .toList();
    }
}
