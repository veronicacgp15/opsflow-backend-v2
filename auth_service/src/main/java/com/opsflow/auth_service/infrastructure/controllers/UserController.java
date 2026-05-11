package com.opsflow.auth_service.infrastructure.controllers;

import com.opsflow.auth_service.application.dtos.*;
import com.opsflow.auth_service.application.dtos.request.ForgotPasswordRequest;
import com.opsflow.auth_service.application.dtos.request.LoginRequest;
import com.opsflow.auth_service.application.dtos.request.ResetPasswordRequest;
import com.opsflow.auth_service.application.dtos.request.SignupRequest;
import com.opsflow.auth_service.application.dtos.request.TokenRefreshRequest;
import com.opsflow.auth_service.application.services.PasswordResetService;
import com.opsflow.auth_service.application.services.PermissionService;
import com.opsflow.auth_service.application.services.RefreshTokenService;
import com.opsflow.auth_service.application.services.UserService;
import com.opsflow.auth_service.domain.models.UserDomain;
import com.opsflow.auth_service.infrastructure.repositories.UserRepository;
import com.opsflow.auth_service.infrastructure.repositories.VerificationTokenRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.opsflow.auth_service.domain.constants.AuthConstants.*;

@CrossOrigin(origins = {"http://localhost:4200/"}, maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class UserController {

    public record PasswordChangeRequest(String newPassword) {}

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;
    private final JwtUtils jwtUtils;

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;

    public UserController(UserService userService,
                          PasswordResetService passwordResetService,
                          RefreshTokenService refreshTokenService,
                          PermissionService permissionService,
                          JwtUtils jwtUtils,
                          VerificationTokenRepository tokenRepository,
                          UserRepository userRepository,
                          AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
        this.permissionService = permissionService;
        this.jwtUtils = jwtUtils;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
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
        userDomain.setEnabled(false);

        userDomain.setOrganizationId(DEFAULT_ORGANIZATION);

        userDomain.setRoles(List.of(ROLE_USER));

        userService.save(userDomain);

        return ResponseEntity.ok(new MessageResponse("Usuario registrado. Revisa tu correo para verificar tu cuenta."));
    }

    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token) {
        var verificationTokenOpt = tokenRepository.findByToken(token);
        if (verificationTokenOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Token de verificacion invalido."));
        }

        var verificationToken = verificationTokenOpt.get();
        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(verificationToken);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("El token de verificacion ha expirado."));
        }

        var user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.delete(verificationToken);

        return ResponseEntity.ok(new MessageResponse("Correo verificado correctamente. Ya puedes iniciar sesion."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.ok(
                new MessageResponse(
                        "Si el correo existe en nuestro sistema, recibiras instrucciones para restablecer tu contrasena."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(new MessageResponse("Contrasena actualizada correctamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
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

    @GetMapping("/me/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Set<String>> getMyPermissions(Authentication authentication) {
        Set<String> codes = permissionService
                .findEffectivePermissionCodesByUsername(authentication.getName());
        return ResponseEntity.ok(new TreeSet<>(codes));
    }
}