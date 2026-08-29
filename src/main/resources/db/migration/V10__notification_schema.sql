-- V10__notification_schema.sql
-- Notification bounded context tables

CREATE TABLE IF NOT EXISTS notification.notifications (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_user_id ON notification.notifications(user_id);
CREATE INDEX idx_notifications_status ON notification.notifications(status);

-- Idempotency table: tracks which event IDs have already been processed
-- to prevent duplicate notifications from at-least-once delivery.
CREATE TABLE IF NOT EXISTS notification.processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
