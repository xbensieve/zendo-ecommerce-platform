package com.zendo.cart.application;

import com.zendo.cart.domain.Cart;
import com.zendo.cart.domain.CartException;
import com.zendo.cart.domain.CartRepository;
import com.zendo.catalog.api.CatalogQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CartUseCases {

    private final CartRepository cartRepository;
    private final CatalogQueryApi catalogQueryApi;

    public CartUseCases(CartRepository cartRepository, CatalogQueryApi catalogQueryApi) {
        this.cartRepository = cartRepository;
        this.catalogQueryApi = catalogQueryApi;
    }

    @Transactional
    public Cart getOrCreateActiveCart(String customerId) {
        return cartRepository.findActiveCartByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart newCart = Cart.initialize(customerId);
                    cartRepository.save(newCart);
                    return newCart;
                });
    }

    @Transactional
    public void addItemToCart(String customerId, String vendorId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new CartException("Quantity must be greater than zero");
        }

        var variantInfo = catalogQueryApi.getVariantInfo(vendorId, sku)
                .orElseThrow(() -> new CartException("Product variant not found or inactive"));

        Cart cart = getOrCreateActiveCart(customerId);
        
        cart.addItem(
                UUID.fromString(vendorId), 
                UUID.fromString(variantInfo.productId()), 
                sku, 
                quantity
        );
        
        cartRepository.save(cart);
    }

    @Transactional
    public void removeItemFromCart(String customerId, String sku) {
        Cart cart = cartRepository.findActiveCartByCustomerId(customerId)
                .orElseThrow(() -> new CartException("Active cart not found"));
        
        cart.removeItem(sku);
        cartRepository.save(cart);
    }

    @Transactional
    public void updateItemQuantity(String customerId, String sku, int newQuantity) {
        Cart cart = cartRepository.findActiveCartByCustomerId(customerId)
                .orElseThrow(() -> new CartException("Active cart not found"));
        
        cart.changeItemQuantity(sku, newQuantity);
        cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(String customerId) {
        Cart cart = cartRepository.findActiveCartByCustomerId(customerId)
                .orElseThrow(() -> new CartException("Active cart not found"));
        
        cart.clear();
        cartRepository.save(cart);
    }

    @Transactional
    public void checkoutCart(String customerId) {
        Cart cart = cartRepository.findActiveCartByCustomerId(customerId)
                .orElseThrow(() -> new CartException("Active cart not found"));
        
        cart.checkout();
        cartRepository.save(cart);
    }
    
    @Transactional
    public void clearCartById(String cartId) {
        // This is idempotent: if it's already checked out, it will still just set it to CHECKED_OUT (if allowed) or we can check status.
        // Wait, cart.checkout() throws CartException if it's not ACTIVE.
        // So we should only checkout if it's ACTIVE.
        cartRepository.findById(UUID.fromString(cartId)).ifPresent(cart -> {
            if (cart.getStatus() == com.zendo.cart.domain.CartStatus.ACTIVE) {
                cart.checkout();
                cartRepository.save(cart);
            }
        });
    }
}
