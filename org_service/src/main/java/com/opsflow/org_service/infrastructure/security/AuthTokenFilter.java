package com.opsflow.org_service.infrastructure.security;

import com.opsflow.common.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.opsflow.org_service.domain.constants.OrgConstants.ERROR_AUTENTICACION_DEL_USUARIO;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private final JwtUtils jwtUtils;

    private static final List<String> EXCLUDE_URLS = Arrays.asList(
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html"
    );

    public AuthTokenFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (EXCLUDE_URLS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateAccessToken(jwt)) {
                String username = jwtUtils.getUsernameFromToken(jwt);
                List<String> roles = jwtUtils.getRolesFromToken(jwt);

                if (username != null && roles != null) {
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, jwt, authorities);

                    Map<String, Object> details = new HashMap<>();
                    details.put("webDetails", new WebAuthenticationDetailsSource().buildDetails(request));
                    details.put("organizationId", jwtUtils.getOrganizationIdFromToken(jwt));
                    details.put("userId", jwtUtils.getUserIdFromToken(jwt));
                    authentication.setDetails(details);
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);


                    if (!"GET".equalsIgnoreCase(request.getMethod()) && path.startsWith("/org")) {
                        log.info("AuthTokenFilter [{} {}] user='{}' authoritiesFromJwt={}",
                                request.getMethod(), path, username, roles);
                    }
                } else {
                    log.warn("AuthTokenFilter [{} {}] JWT valido pero username/roles nulos (username='{}', roles={})",
                            request.getMethod(), path, username, roles);
                }
            } else if (jwt == null) {
                log.debug("AuthTokenFilter [{} {}] sin Authorization Bearer", request.getMethod(), path);
            } else {
                log.warn("AuthTokenFilter [{} {}] JWT presente pero invalido", request.getMethod(), path);
            }
        } catch (Exception e) {
            log.error(ERROR_AUTENTICACION_DEL_USUARIO + " path={} method={} err={}",
                    path, request.getMethod(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
