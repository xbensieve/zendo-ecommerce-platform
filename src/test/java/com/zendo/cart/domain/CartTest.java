package com.zendo.cart.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    void initialize_CreatesActiveCart() {
        Cart cart = Cart.initialize("customer123");

        assertEquals("customer123", cart.getCustomerId());
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void addItem_AddsNewItem() {
        Cart cart = Cart.initialize("customer123");
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        cart.addItem(vendorId, productId, "SKU-1", 2);

        assertEquals(1, cart.getItems().size());
        CartItem item = cart.getItems().get(0);
        assertEquals("SKU-1", item.getSku());
        assertEquals(2, item.getQuantity());
    }

    @Test
    void addItem_IncrementsQuantityForExistingSku() {
        Cart cart = Cart.initialize("customer123");
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        cart.addItem(vendorId, productId, "SKU-1", 2);
        cart.addItem(vendorId, productId, "SKU-1", 3);

        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void removeItem_RemovesItem() {
        Cart cart = Cart.initialize("customer123");
        UUID vendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        cart.addItem(vendorId, productId, "SKU-1", 2);
        cart.removeItem("SKU-1");

        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void checkout_TransitionsToCheckedOut() {
        Cart cart = Cart.initialize("customer123");
        cart.addItem(UUID.randomUUID(), UUID.randomUUID(), "SKU-1", 2);

        cart.checkout();

        assertEquals(CartStatus.CHECKED_OUT, cart.getStatus());
    }

    @Test
    void checkout_FailsIfEmpty() {
        Cart cart = Cart.initialize("customer123");

        assertThrows(CartException.class, cart::checkout);
    }
}
