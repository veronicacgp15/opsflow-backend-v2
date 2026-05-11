package com.opsflow.org_service.infrastructure.adapters.messaging;

import com.opsflow.org_service.application.events.UserRegisteredEvent;
import com.opsflow.org_service.application.services.OrganizationUserService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.opsflow.org_service.domain.constants.OrgConstants.*;

@Component
public class UserRegisteredEventListener {

    private final OrganizationUserService organizationUserService;

    public UserRegisteredEventListener(OrganizationUserService organizationUserService) {
        this.organizationUserService = organizationUserService;
    }

    @RabbitListener(queues = AUTH_USER_REGISTERED_QUEUE)
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        System.out.println(ORG_SERVICE_EVENTO_DE_USUARIO_REGISTRADO_RECIBIDO +
                event.username() + " para Org ID: " + event.organizationId());
        
        try {
            organizationUserService.associateUserToOrganization(event.id(), event.organizationId());
        } catch (Exception e) {
            System.err.println(ERROR_AL_ASOCIAR_USUARIO_A_ORGANIZACION + e.getMessage());
        }
    }
}
