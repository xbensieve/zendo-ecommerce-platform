CREATE SCHEMA IF NOT EXISTS security_ctx;

CREATE TABLE security_ctx.user_credentials (
    user_id VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

ALTER TABLE vendor.vendors ADD COLUMN owner_user_id VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
