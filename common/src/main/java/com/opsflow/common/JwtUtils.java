package com.opsflow.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    public static final String ORGANIZATION_ID = "organizationId";
    public static final String USER_ID = "userId";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration:3600000}")
    private long expiration;

    public String generateAccessToken(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username;
        
        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = principal.toString();
        }
        
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Long organizationId = getOrganizationIdFromAuthentication(authentication);
        Long userId = getUserIdFromAuthentication(authentication);

        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles)

                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey());

        if (organizationId != null) builder.claim(ORGANIZATION_ID, organizationId);
        if (userId != null) builder.claim(USER_ID, userId);

        return builder.compact();
    }

    public boolean validateAccessToken(String token) {
        try {
            Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return getClaim(token, claims -> (List<String>) claims.get("roles"));
    }

    public Long getOrganizationIdFromToken(String token) {
        return getLongClaim(token, ORGANIZATION_ID);
    }

    public Long getUserIdFromToken(String token) {
        return getLongClaim(token, USER_ID);
    }

    private Long getLongClaim(String token, String claimName) {
        return getClaim(token, claims -> {
            Object value = claims.get(claimName);
            if (value == null) return null;
            if (value instanceof Number number) return number.longValue();
            return Long.parseLong(value.toString());
        });
    }

    public <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public boolean hasRole(Authentication authentication, String role) {
        String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals(targetRole));
    }

    public Long getOrganizationIdFromAuthentication(Authentication authentication) {
        return getClaimFromDetails(authentication, ORGANIZATION_ID);
    }

    public Long getUserIdFromAuthentication(Authentication authentication) {
        return getClaimFromDetails(authentication, USER_ID);
    }

    private Long getClaimFromDetails(Authentication authentication, String key) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object value = details.get(key);
            if (value instanceof Number number) return number.longValue();
            if (value != null) {
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}