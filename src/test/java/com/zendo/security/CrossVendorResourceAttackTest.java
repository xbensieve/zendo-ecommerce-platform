package com.zendo.security;

import com.zendo.catalog.application.CatalogUseCases;
import com.zendo.catalog.domain.Product;
import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import com.zendo.inventory.application.InventoryUseCases;
import com.zendo.security.application.TokenService;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import com.zendo.vendor.domain.Vendor;
import com.zendo.vendor.domain.VendorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CrossVendorResourceAttackTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository userCredentialsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CatalogUseCases catalogUseCases;

    @Autowired
    private InventoryUseCases inventoryUseCases;

    @Autowired
    private com.zendo.inventory.domain.InventoryRepository inventoryRepository;

    private record VendorContext(String userId, String token, Vendor vendor) {}

    private VendorContext createVendorContext(String name) {
        String email = name + "_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, name, "Vendor");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.VENDOR
        );
        userCredentialsRepository.save(credentials);

        String token = tokenService.generateToken(user.getId().value(), List.of("VENDOR"), credentials.getSecurityVersion());

        Vendor vendor = Vendor.reconstitute(
                com.zendo.vendor.domain.VendorId.generate(),
                new com.zendo.vendor.domain.VendorName(name + " Store"),
                com.zendo.vendor.domain.VendorStatus.ACTIVE,
                user.getId().value()
        );
        vendorRepository.save(vendor);

        return new VendorContext(user.getId().value(), token, vendor);
    }

    @Test
    @DisplayName("Cross-Vendor Attack: Vendor A cannot create product for Vendor B (HTTP 403)")
    void vendorA_cannotCreateProductForVendorB() {
        VendorContext vendorA = createVendorContext("vendor_a");
        VendorContext vendorB = createVendorContext("vendor_b");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(vendorA.token());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"vendorId\":\"%s\",\"name\":\"Malicious Product\",\"description\":\"Testing cross-vendor isolation\"}",
                vendorB.vendor().getId().value().toString()
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/products", HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Cross-Vendor Attack: Vendor A cannot modify inventory for Vendor B's product (HTTP 403)")
    void vendorA_cannotAdjustInventoryForVendorB_Product() {
        VendorContext vendorA = createVendorContext("vendor_a_inv");
        VendorContext vendorB = createVendorContext("vendor_b_inv");

        // Vendor B creates a legitimate product
        String productIdStr = catalogUseCases.createProduct(
                vendorB.vendor().getId().value().toString(), "Product B", "Description B"
        );
        UUID productId = UUID.fromString(productIdStr);
        catalogUseCases.addVariant(productIdStr, "SKU-B", new BigDecimal("50.00"), "USD");
        inventoryRepository.save(com.zendo.inventory.domain.InventoryItem.initialize(productId, vendorB.vendor().getId().value()));

        // Vendor A attempts to adjust stock for Vendor B's product
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(vendorA.token());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"newOnHandQty\": 99999, \"referenceId\": \"ATTACK-ADJUST\"}";

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/inventory/" + productId + "/adjust",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
