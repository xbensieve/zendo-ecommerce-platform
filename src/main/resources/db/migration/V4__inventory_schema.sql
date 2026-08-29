-- V4__inventory_schema.sql

CREATE TABLE inventory.inventory_items (
    product_id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL,
    on_hand_quantity INTEGER NOT NULL CHECK (on_hand_quantity >= 0),
    reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0),
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT inventory_math_check CHECK (available_quantity = on_hand_quantity - reserved_quantity)
);

CREATE TABLE inventory.inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES inventory.inventory_items(product_id),
    type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    reference_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(product_id, type, reference_id)
);
