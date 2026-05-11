package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.domain.models.UserDomain;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<UserDomain> findAll();
    Optional<UserDomain> findById(Long id);
    UserDomain save(UserDomain userDomain);
    Optional<UserDomain> update(Long id, UserDomain userDomain);
    void delete(Long id);
    Optional<UserDomain> findByUsername(String username);
    List<UserDomain> findByOrganizationId(Long organizationId);
    void changePassword(Long userId, String newPassword);
    void deactivateAccount(Long userId);
    void activateAccount(Long userId);
    Optional<UserDomain> updateRoles(Long userId, List<String> roles);
    Optional<UserDomain> assignOrganizationManager(Long organizationId, Long userId);
}
