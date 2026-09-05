-- V18__add_security_version_to_user_credentials.sql
ALTER TABLE security_ctx.user_credentials 
    ADD COLUMN security_version INTEGER NOT NULL DEFAULT 1;
