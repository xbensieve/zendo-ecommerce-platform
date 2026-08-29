package com.zendo.vendor.api.rest;

import com.zendo.vendor.application.VendorUseCases;
import com.zendo.shared.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorUseCases vendorUseCases;

    public VendorController(VendorUseCases vendorUseCases) {
        this.vendorUseCases = vendorUseCases;
    }

    record OnboardRequest(String name) {}

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('CUSTOMER')")
    public ResponseEntity<String> onboardVendor(@RequestBody OnboardRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        String id = vendorUseCases.onboardVendor(request.name(), user.getUserId());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateVendor(@PathVariable String id) {
        vendorUseCases.activateVendor(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> suspendVendor(@PathVariable String id) {
        vendorUseCases.suspendVendor(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateVendor(@PathVariable String id) {
        vendorUseCases.deactivateVendor(id);
        return ResponseEntity.ok().build();
    }
}
