package com.zendo;

import com.zendo.cart.application.CartUseCases;
import com.zendo.cart.domain.Cart;
import com.zendo.cart.domain.CartStatus;
import com.zendo.catalog.api.CatalogQueryApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CartIntegrationTest {

    @Autowired
    private CartUseCases cartUseCases;

    @MockBean
    private CatalogQueryApi catalogQueryApi;

    @Test
    void cartLifecycle_WorksAsExpected() {
        String customerId = "cust-123";
        String vendorId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String sku = "TEST-SKU";

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo(productId, vendorId, sku, "Test Product", new BigDecimal("10.00"), "USD"))
        );

        // 1. Get or Create Cart
        Cart cart = cartUseCases.getOrCreateActiveCart(customerId);
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertEquals(0, cart.getItems().size());

        // 2. Add item
        cartUseCases.addItemToCart(customerId, vendorId, sku, 2);
        
        Cart updatedCart = cartUseCases.getOrCreateActiveCart(customerId);
        assertEquals(1, updatedCart.getItems().size());
        assertEquals(2, updatedCart.getItems().get(0).getQuantity());

        // 3. Checkout
        cartUseCases.checkoutCart(customerId);
        
        // 4. Next getOrCreate should return a NEW empty cart
        Cart newCart = cartUseCases.getOrCreateActiveCart(customerId);
        assertEquals(CartStatus.ACTIVE, newCart.getStatus());
        assertEquals(0, newCart.getItems().size());
    }
}
