-- V6__order_schema.sql

CREATE TABLE order_ctx.parent_orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE order_ctx.child_orders (
    id UUID PRIMARY KEY,
    parent_order_id UUID NOT NULL REFERENCES order_ctx.parent_orders(id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL
);

CREATE TABLE order_ctx.order_items (
    id UUID PRIMARY KEY,
    child_order_id UUID NOT NULL REFERENCES order_ctx.child_orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(19, 4) NOT NULL
);

CREATE TABLE order_ctx.idempotency_keys (
    key_value VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
