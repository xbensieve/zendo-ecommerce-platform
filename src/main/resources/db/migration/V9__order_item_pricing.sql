ALTER TABLE order_ctx.order_items 
RENAME COLUMN unit_price TO final_unit_price;

ALTER TABLE order_ctx.order_items 
ADD COLUMN original_unit_price DECIMAL(19, 4) NOT NULL DEFAULT 0,
ADD COLUMN applied_discount DECIMAL(19, 4) NOT NULL DEFAULT 0,
ADD COLUMN promotion_ref VARCHAR(255);

-- To handle existing data
UPDATE order_ctx.order_items
SET original_unit_price = final_unit_price;
