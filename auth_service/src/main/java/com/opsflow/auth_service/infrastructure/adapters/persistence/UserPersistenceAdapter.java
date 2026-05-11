package com.opsflow.auth_service.infrastructure.adapters.persistence;

import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.auth_service.domain.ports.out.RoleRepositoryPort;
import com.opsflow.auth_service.domain.ports.out.UserRepositoryPort;
import com.opsflow.auth_service.infrastructure.entities.Role;
import com.opsflow.auth_service.infrastructure.entities.User;
import com.opsflow.auth_service.infrastructure.UserMapper;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RoleRepositoryPort roleRepositoryPort;

    public UserPersistenceAdapter(UserRepository userRepository,
                                  UserMapper userMapper,
                                  RoleRepositoryPort roleRepositoryPort) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.roleRepositoryPort = roleRepositoryPort;
    }

    @Override
    public Optional<UserDomain> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<UserDomain> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<UserDomain> findById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDomain);
    }

    @Override
    public UserDomain save(UserDomain userDomain) {
        User entity = userMapper.toEntity(userDomain);
        User savedEntity = userRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public List<UserDomain> findAll() {
        return StreamSupport.stream(userRepository.findAll().spliterator(), false)
                .map(userMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public Optional<UserDomain> update(Long id, UserDomain userDomain) {
        return userRepository.findById(id)
                .map(existingEntity -> {
                    existingEntity.setName(userDomain.getName());
                    existingEntity.setLastname(userDomain.getLastname());
                    existingEntity.setEmail(userDomain.getEmail());
                    if (userDomain.getPassword() != null && !userDomain.getPassword().isEmpty()) {
                        existingEntity.setPassword(userDomain.getPassword());
                    }
                    existingEntity.setEnabled(userDomain.getEnabled() != null ? userDomain.getEnabled() : false);
                    existingEntity.setOrganizationId(userDomain.getOrganizationId());

                    if (userDomain.getRoles() != null && !userDomain.getRoles().isEmpty()) {
                        List<Role> newRoles = userDomain.getRoles().stream()
                                .map(roleName -> roleRepositoryPort.findByName(roleName)
                                        .orElseThrow(() -> new RuntimeException("Error: Role " + roleName + " is not found.")))
                                .toList();
                        existingEntity.setRoles(newRoles);
                    } else {
                        existingEntity.getRoles().clear();
                    }


                    User savedEntity = userRepository.save(existingEntity);
                    return userMapper.toDomain(savedEntity);
                });
    }
}
