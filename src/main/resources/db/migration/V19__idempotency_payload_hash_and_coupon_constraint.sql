-- Migration V19: Add payload_hash to order idempotency keys and enforce coupon usage constraint
ALTER TABLE order_ctx.idempotency_keys ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);

ALTER TABLE promotion.coupons ADD CONSTRAINT chk_coupon_uses CHECK (current_uses <= max_uses);
