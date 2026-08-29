package com.zendo.vendor.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VendorTest {

    @Test
    void shouldCreateNewVendorWithPendingStatus() {
        Vendor vendor = Vendor.onboard(new VendorName("Test Vendor"), "owner_123");
        
        assertNotNull(vendor.getId());
        assertEquals(VendorStatus.PENDING, vendor.getStatus());
        assertEquals("Test Vendor", vendor.getName().value());
        assertEquals(1, vendor.getDomainEvents().size());
        assertTrue(vendor.getDomainEvents().get(0) instanceof VendorOnboarded);
    }

    @Test
    void shouldDeactivateVendor() {
        Vendor vendor = Vendor.onboard(new VendorName("Test Vendor"), "owner_123");
        vendor.activate();
        vendor.deactivate();
        assertEquals(VendorStatus.DEACTIVATED, vendor.getStatus());
        assertTrue(vendor.getDomainEvents().get(2) instanceof VendorDeactivated);
    }

    @Test
    void shouldActivateVendor() {
        Vendor vendor = Vendor.onboard(new VendorName("Test Vendor"), "owner_123");
        vendor.activate();
        assertEquals(VendorStatus.ACTIVE, vendor.getStatus());
        assertTrue(vendor.getDomainEvents().get(1) instanceof VendorActivated);
    }

    @Test
    void shouldSuspendVendor() {
        Vendor vendor = Vendor.onboard(new VendorName("Test Vendor"), "owner_123");
        vendor.activate();
        vendor.suspend();
        assertEquals(VendorStatus.SUSPENDED, vendor.getStatus());
    }

    @Test
    void suspend_ThrowsExceptionIfPending() {
        Vendor vendor = Vendor.onboard(new VendorName("Acme Corp"), "owner_123");
        assertThrows(VendorException.class, vendor::suspend);
    }
}
