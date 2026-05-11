package com.opsflow.auth_service.application.services;

import com.opsflow.auth_service.application.events.UserRegisteredEvent;
import com.opsflow.auth_service.application.ports.UserEventPublisher;
import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.auth_service.domain.ports.out.UserRepositoryPort;
import com.opsflow.auth_service.infrastructure.UserMapper;
import com.opsflow.auth_service.infrastructure.entities.VerificationToken;
import com.opsflow.auth_service.infrastructure.repositories.VerificationTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.opsflow.auth_service.domain.constants.AuthConstants.ROLE_MANAGER;
import static com.opsflow.auth_service.domain.constants.AuthConstants.ROLE_USER;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepositoryPort userRepositoryPort,
                           PasswordEncoder passwordEncoder,
                           UserEventPublisher userEventPublisher,
                           VerificationTokenRepository tokenRepository,
                           EmailService emailService,
                           UserMapper userMapper) {

        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.userEventPublisher = userEventPublisher;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDomain> findAll() {
        return userRepositoryPort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDomain> findById(Long id) {
        return userRepositoryPort.findById(id);
    }

    @Override
    @Transactional
    public UserDomain save(UserDomain userDomain) {
        if (userDomain.getEnabled() == null) {
            userDomain.setEnabled(false);
        }
        ensureSingleActiveManagerOnCreate(userDomain);
        userDomain.setPassword(passwordEncoder.encode(userDomain.getPassword()));

        var savedUserDomain = userRepositoryPort.save(userDomain);


        var token = java.util.UUID.randomUUID().toString();

        var userEntity = userMapper.toEntity(savedUserDomain);
        var verificationToken = new VerificationToken(token, userEntity);
        tokenRepository.save(verificationToken);

        var event = new UserRegisteredEvent(
                savedUserDomain.getId(),
                savedUserDomain.getUsername(),
                savedUserDomain.getEmail(),
                savedUserDomain.getOrganizationId()
        );
        userEventPublisher.publishUserRegistered(event);

        emailService.sendVerificationEmail(savedUserDomain.getEmail(), token);

        return savedUserDomain;
    }

    @Override
    @Transactional
    public Optional<UserDomain> update(Long id, UserDomain userDomain) {
        return userRepositoryPort.findById(id).flatMap(current -> {
            ensureManagerCardinalityIfAffected(current, userDomain);
            return userRepositoryPort.update(id, userDomain);
        });
    }

    @Override
    @Transactional
    public void delete(Long id) {
        userRepositoryPort.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDomain> findByUsername(String username) {
        return userRepositoryPort.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDomain> findByOrganizationId(Long organizationId) {
        return userRepositoryPort.findAll().stream()
                .filter(user -> organizationId.equals(user.getOrganizationId()))
                .toList();
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newPassword) {
        userRepositoryPort.findById(userId).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepositoryPort.update(userId, user);
        });
    }

    @Override
    @Transactional
    public void deactivateAccount(Long userId) {
        userRepositoryPort.findById(userId).ifPresent(current -> {
            UserDomain candidate = copyUser(current);
            candidate.setEnabled(false);
            ensureManagerCardinalityIfAffected(current, candidate);
            userRepositoryPort.update(userId, candidate);
        });
    }

    @Override
    @Transactional
    public void activateAccount(Long userId) {
        userRepositoryPort.findById(userId).ifPresent(current -> {
            UserDomain candidate = copyUser(current);
            candidate.setEnabled(true);
            ensureManagerCardinalityIfAffected(current, candidate);
            userRepositoryPort.update(userId, candidate);
        });
    }

    @Override
    @Transactional
    public Optional<UserDomain> updateRoles(Long userId, List<String> roles) {
        return userRepositoryPort.findById(userId).map(current -> {
            UserDomain candidate = copyUser(current);
            candidate.setRoles(new ArrayList<>(roles));
            ensureManagerCardinalityIfAffected(current, candidate);
            return userRepositoryPort.update(userId, candidate).orElse(candidate);
        });
    }

    @Override
    @Transactional
    public Optional<UserDomain> assignOrganizationManager(Long organizationId, Long userId) {
        UserDomain target = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalStateException("El manager asignado debe estar activo.");
        }

        Long sourceOrganizationId = target.getOrganizationId();
        List<UserDomain> targetOrganizationUsers = new ArrayList<>(findByOrganizationId(organizationId));
        boolean targetAlreadyInOrganization = targetOrganizationUsers.stream()
                .anyMatch(user -> user.getId().equals(userId));

        UserDomain promotedTarget = copyUser(target);
        promotedTarget.setOrganizationId(organizationId);
        promotedTarget.setRoles(managerRoles(target));

        LinkedHashSet<Long> usersToPersist = Stream.concat(
                        Stream.of(promotedTarget.getId()),
                        targetOrganizationUsers.stream()
                                .filter(user -> !user.getId().equals(userId))
                                .filter(user -> hasRole(user, ROLE_MANAGER))
                                .map(UserDomain::getId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UserDomain> projectedTargetUsers = targetOrganizationUsers.stream()
                .map(user -> {
                    if (user.getId().equals(userId)) {
                        return copyUser(promotedTarget);
                    }
                    if (hasRole(user, ROLE_MANAGER)) {
                        UserDomain demoted = copyUser(user);
                        demoted.setRoles(rolesWithoutManager(user));
                        return demoted;
                    }
                    return copyUser(user);
                })
                .collect(Collectors.toCollection(ArrayList::new));
        if (!targetAlreadyInOrganization) {
            projectedTargetUsers.add(copyUser(promotedTarget));
        }

        ensureExactSingleActiveManager(organizationId, projectedTargetUsers);

        if (sourceOrganizationId != null && !sourceOrganizationId.equals(organizationId)) {
            List<UserDomain> projectedSourceUsers = findByOrganizationId(sourceOrganizationId).stream()
                    .filter(user -> !user.getId().equals(userId))
                    .map(this::copyUser)
                    .toList();
            ensureExactSingleActiveManager(sourceOrganizationId, projectedSourceUsers);
        }

        projectedTargetUsers.stream()
                .filter(user -> usersToPersist.contains(user.getId()))
                .forEach(user -> userRepositoryPort.update(user.getId(), user)
                        .orElseThrow(() -> new IllegalArgumentException("No se pudo actualizar el relevo de manager.")));

        return userRepositoryPort.findById(userId);
    }

    private void ensureSingleActiveManagerOnCreate(UserDomain candidate) {
        if (candidate == null || candidate.getOrganizationId() == null || !isActiveManager(candidate)) {
            return;
        }
        List<UserDomain> projectedUsers = new ArrayList<>(findByOrganizationId(candidate.getOrganizationId()));
        projectedUsers.add(copyUser(candidate));
        ensureExactSingleActiveManager(candidate.getOrganizationId(), projectedUsers);
    }

    private void ensureManagerCardinalityIfAffected(UserDomain before, UserDomain after) {
        if (!affectsManagerCardinality(before, after)) {
            return;
        }

        Set<Long> affectedOrganizationIds = Stream.of(before, after)
                .filter(Objects::nonNull)
                .map(UserDomain::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        affectedOrganizationIds.forEach(organizationId ->
                ensureExactSingleActiveManager(organizationId, projectOrganizationUsers(organizationId, before, after)));
    }

    private List<UserDomain> projectOrganizationUsers(Long organizationId, UserDomain before, UserDomain after) {
        List<UserDomain> projectedUsers = new ArrayList<>(findByOrganizationId(organizationId));

        if (before != null && before.getId() != null) {
            projectedUsers.removeIf(user -> before.getId().equals(user.getId()));
        }
        if (after != null && after.getId() != null && organizationId.equals(after.getOrganizationId())) {
            projectedUsers.add(copyUser(after));
        }
        return projectedUsers;
    }

    private boolean affectsManagerCardinality(UserDomain before, UserDomain after) {
        return hasRole(before, ROLE_MANAGER) || hasRole(after, ROLE_MANAGER);
    }

    private void ensureExactSingleActiveManager(Long organizationId, List<UserDomain> users) {
        long activeManagers = users.stream().filter(this::isActiveManager).count();
        if (activeManagers == 1) {
            return;
        }
        if (activeManagers == 0) {
            throw new IllegalStateException(
                    "La organizacion " + organizationId +
                            " debe tener exactamente un manager activo. Designa un sucesor antes de continuar.");
        }
        throw new IllegalStateException(
                "La organizacion " + organizationId +
                        " solo puede tener un manager activo. Usa la operacion de cambio de mando para relevar al actual.");
    }

    private boolean isActiveManager(UserDomain user) {
        return user != null
                && user.getOrganizationId() != null
                && Boolean.TRUE.equals(user.getEnabled())
                && hasRole(user, ROLE_MANAGER);
    }

    private boolean hasRole(UserDomain user, String role) {
        if (user == null) {
            return false;
        }
        return safeRoles(user).stream().anyMatch(role::equals);
    }

    private List<String> safeRoles(UserDomain user) {
        return user.getRoles() != null ? user.getRoles() : List.of();
    }

    private List<String> rolesWithoutManager(UserDomain user) {
        LinkedHashSet<String> roles = new LinkedHashSet<>(safeRoles(user));
        roles.remove(ROLE_MANAGER);
        roles.add(ROLE_USER);
        return new ArrayList<>(roles);
    }

    private List<String> managerRoles(UserDomain user) {
        LinkedHashSet<String> roles = new LinkedHashSet<>(safeRoles(user));
        roles.add(ROLE_USER);
        roles.add(ROLE_MANAGER);
        return new ArrayList<>(roles);
    }

    private UserDomain copyUser(UserDomain user) {
        UserDomain copy = new UserDomain();
        copy.setId(user.getId());
        copy.setName(user.getName());
        copy.setLastname(user.getLastname());
        copy.setUsername(user.getUsername());
        copy.setPassword(user.getPassword());
        copy.setEmail(user.getEmail());
        copy.setEnabled(user.getEnabled());
        copy.setOrganizationId(user.getOrganizationId());
        copy.setRoles(new ArrayList<>(safeRoles(user)));
        return copy;
    }
}
