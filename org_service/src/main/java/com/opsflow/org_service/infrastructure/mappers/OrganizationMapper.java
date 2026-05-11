package com.opsflow.org_service.infrastructure.mappers;

import com.opsflow.org_service.domain.models.OrganizationDomain;
import com.opsflow.org_service.infrastructure.entities.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

    public OrganizationDomain toDomain(Organization entity) {
        if (entity == null) return null;
        OrganizationDomain domain = new OrganizationDomain();
        domain.setId(entity.getId());
        domain.setName(entity.getName());
        domain.setTaxId(entity.getTaxId());
        domain.setAddress(entity.getAddress());
        domain.setEmail(entity.getEmail());
        domain.setPhone(entity.getPhone());
        domain.setActive(entity.getActive());
        domain.setPlanLimit(entity.getPlanLimit());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setCreatedByUserId(entity.getCreatedByUserId());
        return domain;
    }

    public Organization toEntity(OrganizationDomain domain) {
        if (domain == null) return null;
        Organization entity = new Organization();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setTaxId(domain.getTaxId());
        entity.setAddress(domain.getAddress());
        entity.setEmail(domain.getEmail());
        entity.setPhone(domain.getPhone());
        entity.setActive(domain.getActive());
        entity.setPlanLimit(domain.getPlanLimit());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setCreatedByUserId(domain.getCreatedByUserId());
        return entity;
    }
}
