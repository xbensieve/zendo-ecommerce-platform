package com.zendo.security.api.rest;

import com.zendo.security.application.AuthUseCases;
import com.zendo.security.application.DuplicateRegistrationException;
import com.zendo.security.application.RegistrationUseCases;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthUseCases authUseCases;
    private final RegistrationUseCases registrationUseCases;

    public AuthController(AuthUseCases authUseCases, RegistrationUseCases registrationUseCases) {
        this.authUseCases = authUseCases;
        this.registrationUseCases = registrationUseCases;
    }

    public record LoginRequest(String email, String password) {}
    
    public record LoginResponse(String token, String type, String userId, String role) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            return ResponseEntity.badRequest().body("Email and password are required");
        }

        try {
            AuthUseCases.AuthResult result = authUseCases.login(request.email(), request.password());
            return ResponseEntity.ok(new LoginResponse(result.token(), "Bearer", result.userId(), result.role()));
        } catch (AuthUseCases.AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    public record RegistrationRequest(String email, String password, String firstName, String lastName) {}
    public record RegistrationResponse(String userId, String email, String role) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request) {
        try {
            var result = registrationUseCases.register(request.email(), request.password(), request.firstName(), request.lastName());
            return ResponseEntity.ok(new RegistrationResponse(result.userId(), result.email(), result.role()));
        } catch (DuplicateRegistrationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
