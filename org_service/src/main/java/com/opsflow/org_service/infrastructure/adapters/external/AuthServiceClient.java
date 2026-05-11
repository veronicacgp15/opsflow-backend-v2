package com.opsflow.org_service.infrastructure.adapters.external;

import com.opsflow.org_service.application.dtos.MessageResponse;
import com.opsflow.org_service.application.dtos.UserProfileDto;
import com.opsflow.org_service.application.dtos.request.CreateRoleRequest;
import com.opsflow.org_service.application.dtos.request.ChangeRoleRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Hidden
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @PostMapping("/auth/roles/create")
    ResponseEntity<?> createRole(@RequestBody CreateRoleRequest request);

    @PutMapping("/auth/roles/users/{userId}/change-role")
    ResponseEntity<MessageResponse> changeUserRole(@PathVariable("userId") Long userId,
                                                   @RequestBody ChangeRoleRequest request);

    @PostMapping("/users/profiles/batch")
    ResponseEntity<List<UserProfileDto>> resolveProfiles(@RequestBody List<Long> userIds);

    @PutMapping("/users/organizations/{orgId}/manager/{userId}")
    ResponseEntity<Object> assignOrganizationManager(@PathVariable("orgId") Long orgId,
                                                     @PathVariable("userId") Long userId);
}
