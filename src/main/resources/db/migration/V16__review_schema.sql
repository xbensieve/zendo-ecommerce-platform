CREATE SCHEMA IF NOT EXISTS review_ctx;

CREATE TABLE review_ctx.reviews (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    order_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    content TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_review_order_item UNIQUE (order_item_id)
);

CREATE INDEX idx_review_product_status ON review_ctx.reviews(product_id, status);
CREATE INDEX idx_review_customer ON review_ctx.reviews(customer_id);
