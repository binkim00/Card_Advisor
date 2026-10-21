ALTER TABLE ledger_entries ADD COLUMN purchase_facts jsonb;
ALTER TABLE cancellations ADD COLUMN received_at date;
UPDATE cancellations SET received_at=(created_at AT TIME ZONE 'Asia/Seoul')::date;
ALTER TABLE cancellations ALTER COLUMN received_at SET NOT NULL;
ALTER TABLE cancellations ALTER COLUMN received_at SET DEFAULT CURRENT_DATE;

ALTER TABLE card_month_states DROP CONSTRAINT card_month_states_source_check;
ALTER TABLE card_month_states ADD CONSTRAINT card_month_states_source_check CHECK(source IN ('SYNTHETIC','USER_INPUT'));
