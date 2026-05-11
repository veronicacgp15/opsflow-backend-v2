package com.opsflow.org_service.application.services;

import com.opsflow.org_service.application.dtos.UserProfileDto;
import com.opsflow.org_service.infrastructure.adapters.external.AuthServiceClient;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(UserProfileResolver.class);

    private final AuthServiceClient authServiceClient;

    public UserProfileResolver(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    public Map<Long, UserProfileDto> resolveByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> uniqueIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            ResponseEntity<List<UserProfileDto>> response =
                    authServiceClient.resolveProfiles(uniqueIds);

            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Respuesta inválida o vacía desde auth-service al intentar resolver {} perfiles. Status: {}",
                        uniqueIds.size(),
                        response != null ? response.getStatusCode() : "NULL");
                return Collections.emptyMap();
            }

            return response.getBody().stream()
                    .filter(Objects::nonNull)
                    .filter(profile -> profile.id() != null)
                    .collect(Collectors.toMap(
                            UserProfileDto::id,
                            Function.identity(),
                            (existing, replacement) -> existing
                    ));

        } catch (FeignException ex) {
            log.warn("Error de comunicación (Feign) con auth-service. Status HTTP: {}. Falló la resolución de {} perfiles: {}",
                    ex.status(), uniqueIds.size(), ex.getMessage());
            return Collections.emptyMap();

        } catch (Exception ex) {
            log.error("Error inesperado al intentar resolver perfiles desde auth-service ({} ids). Causa: {} - {}",
                    uniqueIds.size(), ex.getClass().getSimpleName(), ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
