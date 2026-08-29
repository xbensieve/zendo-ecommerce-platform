package com.zendo.cart.application;

import com.zendo.cart.api.CartQueryApi;
import com.zendo.cart.domain.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CartQueryServiceImpl implements CartQueryApi {

    private final CartRepository cartRepository;

    public CartQueryServiceImpl(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Override
    public Optional<CartDetails> getActiveCartDetails(String customerId) {
        return cartRepository.findActiveCartByCustomerId(customerId)
                .map(cart -> new CartDetails(
                        cart.getId().toString(),
                        cart.getCustomerId(),
                        cart.getStatus().name(),
                        cart.getItems().stream()
                                .map(item -> new CartItemDetails(
                                        item.getVendorId().toString(),
                                        item.getProductId().toString(),
                                        item.getSku(),
                                        item.getQuantity()
                                ))
                                .collect(Collectors.toList())
                ));
    }
}
