CREATE TABLE promotion.flash_sales (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(255) NOT NULL,
    flash_price DECIMAL(19, 4) NOT NULL,
    allocated_quantity INTEGER NOT NULL,
    available_quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fs_alloc CHECK (allocated_quantity > 0),
    CONSTRAINT chk_fs_avail CHECK (available_quantity >= 0 AND available_quantity <= allocated_quantity),
    CONSTRAINT chk_fs_dates CHECK (start_time < end_time)
);

CREATE INDEX idx_flash_sales_product ON promotion.flash_sales(product_id);
CREATE INDEX idx_flash_sales_status ON promotion.flash_sales(status);
CREATE INDEX idx_flash_sales_dates ON promotion.flash_sales(start_time, end_time);
