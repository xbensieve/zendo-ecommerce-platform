CREATE SCHEMA IF NOT EXISTS promotion;

CREATE TABLE promotion.campaigns (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value DECIMAL(19, 4) NOT NULL,
    is_global BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE promotion.campaign_vendor_scopes (
    campaign_id UUID NOT NULL REFERENCES promotion.campaigns(id),
    vendor_id UUID NOT NULL,
    PRIMARY KEY (campaign_id, vendor_id)
);

CREATE TABLE promotion.campaign_product_scopes (
    campaign_id UUID NOT NULL REFERENCES promotion.campaigns(id),
    product_id UUID NOT NULL,
    PRIMARY KEY (campaign_id, product_id)
);

CREATE TABLE promotion.coupons (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value DECIMAL(19, 4) NOT NULL,
    is_global BOOLEAN NOT NULL,
    max_uses INTEGER NOT NULL,
    current_uses INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE promotion.coupon_vendor_scopes (
    coupon_id UUID NOT NULL REFERENCES promotion.coupons(id),
    vendor_id UUID NOT NULL,
    PRIMARY KEY (coupon_id, vendor_id)
);

CREATE TABLE promotion.coupon_product_scopes (
    coupon_id UUID NOT NULL REFERENCES promotion.coupons(id),
    product_id UUID NOT NULL,
    PRIMARY KEY (coupon_id, product_id)
);

-- Index for coupon code lookups
CREATE INDEX idx_coupons_code ON promotion.coupons(code);
