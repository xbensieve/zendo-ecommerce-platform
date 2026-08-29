-- V3__catalog_schema.sql

CREATE TABLE catalog.products (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE catalog.product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    price_amount NUMERIC(15, 4) NOT NULL,
    price_currency VARCHAR(3) NOT NULL,
    UNIQUE(vendor_id, sku)
);
