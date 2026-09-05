package com.zendo.security.api.rest;

import com.zendo.security.api.SecurityCommandApi;
import com.zendo.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final SecurityCommandApi securityCommandApi;

    public AdminUserController(SecurityCommandApi securityCommandApi) {
        this.securityCommandApi = securityCommandApi;
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "Role is required")
            @Pattern(regexp = "(?i)^(CUSTOMER|VENDOR|ADMIN)$", message = "Role must be CUSTOMER, VENDOR, or ADMIN")
            String role
    ) {}

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateRoleRequest request) {
        securityCommandApi.updateUserRole(userId, request.role());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
