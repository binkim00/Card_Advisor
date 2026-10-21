-- Only constrained provider codes. Never retain response messages or payloads.
ALTER TABLE financial_connections ADD COLUMN provider_code varchar(8) CHECK (provider_code ~ '^CF-[0-9]{5}$');
ALTER TABLE financial_connection_operations ADD COLUMN provider_code varchar(8) CHECK (provider_code ~ '^CF-[0-9]{5}$');
