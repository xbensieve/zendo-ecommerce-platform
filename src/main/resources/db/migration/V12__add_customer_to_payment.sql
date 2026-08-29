ALTER TABLE payment.payment_transactions ADD COLUMN customer_id VARCHAR(255);

-- For existing transactions (if any), we'll just set them to a dummy value 
-- since we don't have the data, but for new transactions it will be populated.
UPDATE payment.payment_transactions SET customer_id = 'legacy-customer' WHERE customer_id IS NULL;

ALTER TABLE payment.payment_transactions ALTER COLUMN customer_id SET NOT NULL;
