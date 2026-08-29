-- V5__cart_schema.sql

CREATE TABLE cart.carts (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Partial index to ensure only 1 ACTIVE cart per customer
CREATE UNIQUE INDEX idx_active_cart_per_customer ON cart.carts (customer_id) WHERE status = 'ACTIVE';

CREATE TABLE cart.cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES cart.carts(id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE(cart_id, sku)
);
