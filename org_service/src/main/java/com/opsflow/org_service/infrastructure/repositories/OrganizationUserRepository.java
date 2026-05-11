package com.opsflow.org_service.infrastructure.repositories;

import com.opsflow.org_service.infrastructure.entities.OrganizationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, Long> {
}
