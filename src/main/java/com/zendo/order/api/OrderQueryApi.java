package com.zendo.order.api;

import java.util.UUID;

/**
 * Public query API for the Order bounded context.
 * Exposes minimal read operations to other modules without exposing domain entities or repositories.
 */
public interface OrderQueryApi {
    
    /**
     * Verifies if a specific order item is eligible for review by a given customer.
     * An item is eligible if the parent order belongs to the customer and is in a paid/completed state.
     *
     * @param customerId the ID of the customer attempting to review
     * @param orderItemId the UUID of the specific order item
     * @param productId the UUID of the target product
     * @return true if the customer purchased the item for the specific product and the order is paid
     */
    boolean isEligibleForReview(String customerId, UUID orderItemId, UUID productId);
}
