package com.zendo.vendor.api.rest;

import com.zendo.vendor.application.VendorUseCases;
import com.zendo.shared.security.AuthenticatedUser;
import com.zendo.shared.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendors")
public class VendorController {

    private final VendorUseCases vendorUseCases;

    public VendorController(VendorUseCases vendorUseCases) {
        this.vendorUseCases = vendorUseCases;
    }

    record OnboardRequest(String name) {}

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<String>> onboardVendor(@RequestBody OnboardRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        String id = vendorUseCases.onboardVendor(request.name(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(id));
    }

    public record UpdateStatusRequest(String status) {}

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        if (request.status() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status is required"));
        }
        
        switch (request.status().toUpperCase()) {
            case "ACTIVE":
                vendorUseCases.activateVendor(id);
                break;
            case "SUSPENDED":
                vendorUseCases.suspendVendor(id);
                break;
            case "INACTIVE":
                vendorUseCases.deactivateVendor(id);
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status: " + request.status()));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
