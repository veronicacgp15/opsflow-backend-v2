package com.opsflow.auth_service.infrastructure.security;

import com.opsflow.auth_service.infrastructure.entities.Permission;
import com.opsflow.auth_service.infrastructure.entities.Role;
import com.opsflow.auth_service.infrastructure.entities.User;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Carga el usuario para Spring Security exponiendo TANTO los nombres de rol (e.g.
 * {@code ROLE_ADMIN}) COMO los codigos de permiso asociados a esos roles (e.g.
 * {@code ORG_DEACTIVATE}) como {@link GrantedAuthority}.
 *
 * <p>De este modo:
 * <ul>
 *   <li>El JWT generado por {@code JwtUtils} viaja con ambos tipos de authority en su claim
 *       {@code roles}.</li>
 *   <li>Los microservicios destino pueden gatear sus endpoints con
 *       {@code @PreAuthorize("hasAuthority('CODIGO_PERMISO')")} y NO solo por rol, lo que
 *       permite mover capacidades entre roles desde el modal de permisos sin tener que tocar
 *       codigo ni reasignar nadie a {@code ROLE_ADMIN}.</li>
 *   <li>Las reglas legacy basadas en rol siguen funcionando porque el rol tambien viaja.</li>
 * </ul>
 *
 * <p>El metodo es {@code @Transactional(readOnly = true)} porque {@code Role.permissions} es
 * {@code FetchType.LAZY}; sin la sesion abierta saltaria {@code LazyInitializationException}
 * al iterar los permisos.
 */
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JpaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("Usuario %s no existe", username)));

        // LinkedHashSet preserva orden y deduplica si dos roles comparten el mismo permiso.
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getName() != null) {
                    authorities.add(new SimpleGrantedAuthority(role.getName()));
                }
                if (role.getPermissions() != null) {
                    for (Permission permission : role.getPermissions()) {
                        if (permission.getCode() != null) {
                            authorities.add(new SimpleGrantedAuthority(permission.getCode()));
                        }
                    }
                }
            }
        }

        boolean enabledStatus = user.getEnabled() != null ? user.getEnabled() : false;

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabledStatus,
                true,
                true,
                true,
                authorities);
    }
}
