package com.zendo.cart.infrastructure.persistence;

import com.zendo.cart.domain.Cart;
import com.zendo.cart.domain.CartItem;
import com.zendo.cart.domain.CartRepository;
import com.zendo.cart.domain.CartStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional
public class CartRepositoryImpl implements CartRepository {

    private final SpringDataCartRepository springDataCartRepository;

    public CartRepositoryImpl(SpringDataCartRepository springDataCartRepository) {
        this.springDataCartRepository = springDataCartRepository;
    }

    @Override
    public void save(Cart cart) {
        CartEntity entity = springDataCartRepository.findById(cart.getId())
                .orElseGet(() -> new CartEntity(cart.getId(), cart.getCustomerId(), cart.getStatus().name()));
        
        entity.updateStatus(cart.getStatus().name());
        
        // Sync items
        List<CartItem> domainItems = cart.getItems();
        
        // Remove deleted items
        entity.getItems().removeIf(eItem -> domainItems.stream().noneMatch(dItem -> dItem.getId().equals(eItem.getId())));
        
        for (CartItem dItem : domainItems) {
            Optional<CartItemEntity> existing = entity.getItems().stream()
                    .filter(e -> e.getId().equals(dItem.getId()))
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().updateQuantity(dItem.getQuantity());
            } else {
                entity.addItem(new CartItemEntity(dItem.getId(), dItem.getVendorId(), dItem.getProductId(), dItem.getSku(), dItem.getQuantity()));
            }
        }
        
        springDataCartRepository.save(entity);
    }

    @Override
    public Optional<Cart> findById(UUID id) {
        return springDataCartRepository.findById(id).map(this::mapToDomain);
    }

    @Override
    public Optional<Cart> findActiveCartByCustomerId(String customerId) {
        return springDataCartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE.name())
                .map(this::mapToDomain);
    }
    
    private Cart mapToDomain(CartEntity entity) {
        List<CartItem> items = entity.getItems().stream()
                .map(e -> new CartItem(e.getId(), e.getVendorId(), e.getProductId(), e.getSku(), e.getQuantity()))
                .collect(Collectors.toList());
        return Cart.reconstitute(entity.getId(), entity.getCustomerId(), CartStatus.valueOf(entity.getStatus()), items);
    }
}
