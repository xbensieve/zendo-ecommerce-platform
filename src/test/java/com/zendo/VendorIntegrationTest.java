package com.zendo;

import com.zendo.vendor.application.VendorUseCases;
import com.zendo.vendor.domain.VendorRepository;
import com.zendo.vendor.domain.VendorId;
import com.zendo.vendor.domain.VendorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class VendorIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private VendorUseCases vendorUseCases;

    @Autowired
    private VendorRepository vendorRepository;

    @Test
    void testOnboardAndActivateVendor() {
        // Act
        String vendorId = vendorUseCases.onboardVendor("Integration Test Vendor", "owner_123");

        assertNotNull(vendorId);
        VendorId id = VendorId.fromString(vendorId);

        var vendor = vendorRepository.findById(id).orElseThrow();
        assertEquals("Integration Test Vendor", vendor.getName().value());
        assertEquals(VendorStatus.PENDING, vendor.getStatus());

        // 2. Activate
        vendorUseCases.activateVendor(vendorId);

        var activatedVendor = vendorRepository.findById(id).orElseThrow();
        assertEquals(VendorStatus.ACTIVE, activatedVendor.getStatus());
    }
}
