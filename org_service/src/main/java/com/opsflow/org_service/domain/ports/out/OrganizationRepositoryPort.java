package com.opsflow.org_service.domain.ports.out;

import com.opsflow.org_service.domain.models.OrganizationDomain;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepositoryPort {
    OrganizationDomain save(OrganizationDomain organization);
    Optional<OrganizationDomain> findById(Long id);
    List<OrganizationDomain> findAll();
    void deleteById(Long id);
    Optional<OrganizationDomain> findByTaxId(String taxId);

    List<OrganizationDomain> findByCreatedByUserId(Long createdByUserId);
}
