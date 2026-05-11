package com.opsflow.org_service.infrastructure.repositories;

import com.opsflow.org_service.infrastructure.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByTaxId(String taxId);

    List<Organization> findByCreatedByUserId(Long createdByUserId);
}
