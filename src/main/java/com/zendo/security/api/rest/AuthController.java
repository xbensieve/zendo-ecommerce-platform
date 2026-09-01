package com.zendo.security.api.rest;

import com.zendo.security.application.AuthUseCases;
import com.zendo.security.application.DuplicateRegistrationException;
import com.zendo.security.application.RegistrationUseCases;
import com.zendo.shared.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUseCases authUseCases;
    private final RegistrationUseCases registrationUseCases;

    public AuthController(AuthUseCases authUseCases, RegistrationUseCases registrationUseCases) {
        this.authUseCases = authUseCases;
        this.registrationUseCases = registrationUseCases;
    }

    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email, 
        
        @NotBlank(message = "Password is required")
        String password
    ) {}
    
    public record LoginResponse(String token, String type, String userId, String role) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request) {

        try {
            AuthUseCases.AuthResult result = authUseCases.login(request.email(), request.password());
            return ResponseEntity.ok(ApiResponse.success(new LoginResponse(result.token(), "Bearer", result.userId(), result.role())));
        } catch (AuthUseCases.AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        }
    }

    public record RegistrationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email, 
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password, 
        
        @NotBlank(message = "First name is required")
        String firstName, 
        
        @NotBlank(message = "Last name is required")
        String lastName
    ) {}
    public record RegistrationResponse(String userId, String email, String role) {}

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            var result = registrationUseCases.register(request.email(), request.password(), request.firstName(), request.lastName());
            return ResponseEntity.ok(ApiResponse.success(new RegistrationResponse(result.userId(), result.email(), result.role())));
        } catch (DuplicateRegistrationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
