package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.*;
import com.opsflow.auth_service.application.dtos.request.LoginRequest;
import com.opsflow.auth_service.application.dtos.request.SignupRequest;
import com.opsflow.auth_service.application.dtos.request.TokenRefreshRequest;
import com.opsflow.auth_service.application.services.RefreshTokenService;
import com.opsflow.auth_service.application.services.UserService;
import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.common.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.opsflow.auth_service.domain.constants.AuthConstants.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/auth-legacy")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, 
                          UserService userService,
                          RefreshTokenService refreshTokenService, 
                          JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(),
                        loginRequest.password()));

        var user = userService.findByUsername(loginRequest.username()).orElseThrow();
        
        Map<String, Object> details = new HashMap<>();
        details.put("userId", user.getId());
        details.put("organizationId", user.getOrganizationId());
        
        UsernamePasswordAuthenticationToken authWithDetails = (UsernamePasswordAuthenticationToken) authentication;
        authWithDetails.setDetails(details);

        SecurityContextHolder.getContext().setAuthentication(authWithDetails);
        String jwt = jwtUtils.generateAccessToken(authWithDetails);

        var userDetails = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        var refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());

        return ResponseEntity.ok(new JwtResponse(jwt,
                refreshToken.getId(),
                user.getId(),
                userDetails.getUsername(),
                user.getEmail(),
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {

        if (userService.findByUsername(signUpRequest.username()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(ERROR_USERNAME_IS_ALREADY_TAKEN));
        }

        var userDomain = new UserDomain();
        userDomain.setUsername(signUpRequest.username());
        userDomain.setEmail(signUpRequest.email());
        userDomain.setName(signUpRequest.name());
        userDomain.setLastname(signUpRequest.lastname());
        
        userDomain.setPassword(signUpRequest.password());
        userDomain.setEnabled(true);

        userDomain.setOrganizationId(DEFAULT_ORGANIZATION);

        userDomain.setRoles(List.of(ROLE_USER));

        userService.save(userDomain);
        
        return ResponseEntity.ok(new MessageResponse(USER_REGISTERED_SUCCESSFULLY));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.refreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(token -> {
                    var user = userService.findByUsername(token.getUsername()).orElseThrow();
                    var userDetails = new org.springframework.security.core.userdetails.User(
                            user.getUsername(), user.getPassword(), new ArrayList<>());
                    
                    var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, new ArrayList<>());

                    Map<String, Object> details = new HashMap<>();
                    details.put("userId", user.getId());
                    details.put("organizationId", user.getOrganizationId());
                    authentication.setDetails(details);
                    
                    String tokenStr = jwtUtils.generateAccessToken(authentication);
                    return ResponseEntity.ok(new TokenRefreshResponse(tokenStr, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException(REFRESH_TOKEN_IS_NOT_IN_DATABASE));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> logoutUser(Authentication authentication) {
        refreshTokenService.deleteByUsername(authentication.getName());
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }
}
