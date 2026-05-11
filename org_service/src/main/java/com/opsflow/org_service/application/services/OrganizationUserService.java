package com.opsflow.org_service.application.services;

import com.opsflow.org_service.domain.ports.in.OrganizationUserServicePort;
import com.opsflow.org_service.infrastructure.entities.Organization;
import com.opsflow.org_service.infrastructure.entities.OrganizationUser;
import com.opsflow.org_service.infrastructure.repositories.OrganizationRepository;
import com.opsflow.org_service.infrastructure.repositories.OrganizationUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.opsflow.org_service.domain.constants.OrgConstants.ASOCIADO_A_LA_ORGANIZACION;
import static com.opsflow.org_service.domain.constants.OrgConstants.ORGANIZATION_NOT_FOUND_WITH_ID;

@Service
public class OrganizationUserService implements OrganizationUserServicePort {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationUserService.class);

    private final OrganizationUserRepository organizationUserRepository;
    private final OrganizationRepository organizationRepository;

    public OrganizationUserService(OrganizationUserRepository organizationUserRepository,
                                   OrganizationRepository organizationRepository) {
        this.organizationUserRepository = organizationUserRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional
    public void associateUserToOrganization(Long userId, Long organizationId) {

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException(ORGANIZATION_NOT_FOUND_WITH_ID
                        + organizationId));


        OrganizationUser organizationUser = new OrganizationUser();
        organizationUser.setUserId(userId);
        organizationUser.setOrganization(organization);
        organizationUser.setRole("MEMBER");

        organizationUserRepository.save(organizationUser);

        logger.info("Usuario {}{}{}", userId, ASOCIADO_A_LA_ORGANIZACION, organizationId);
    }
}
