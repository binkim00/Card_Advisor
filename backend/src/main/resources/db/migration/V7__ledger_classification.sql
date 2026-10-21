ALTER TABLE ledger_entries
 ADD COLUMN classification_version bigint NOT NULL DEFAULT 1 CHECK(classification_version>0),
 ADD COLUMN classification_updated_at timestamptz,
 ADD COLUMN original_category text,
 ADD COLUMN original_included boolean;
-- No classification editing existed before V7. Preserve creation values for replay checks.
UPDATE ledger_entries SET original_category=category,original_included=included;
ALTER TABLE ledger_entries ALTER COLUMN original_category SET NOT NULL;
ALTER TABLE ledger_entries ALTER COLUMN original_included SET NOT NULL;
