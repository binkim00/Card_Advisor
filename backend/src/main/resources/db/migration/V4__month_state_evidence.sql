ALTER TABLE card_month_states
 ADD COLUMN untracked_source text NOT NULL DEFAULT 'UNKNOWN',
 ADD COLUMN untracked_as_of date,
 ADD COLUMN qualification_source text NOT NULL DEFAULT 'UNKNOWN',
 ADD COLUMN qualification_as_of date,
 ADD COLUMN qualification_period date;
-- Preserve existing synthetic provenance; do not invent an evidence date.
UPDATE card_month_states SET
 untracked_source=CASE WHEN untracked_performance_won IS NULL THEN 'UNKNOWN' ELSE 'SYNTHETIC' END,
 qualification_source=CASE WHEN current_qualified IS NULL THEN 'UNKNOWN' ELSE 'SYNTHETIC' END,
 qualification_period=(month-interval '1 month')::date;
ALTER TABLE card_month_states
 ALTER COLUMN qualification_period SET NOT NULL,
 ADD CHECK (qualification_period=(month-interval '1 month')::date),
 ADD CHECK (untracked_source IN ('UNKNOWN','SYNTHETIC','USER_INPUT','USER_ESTIMATE')),
 ADD CHECK (qualification_source IN ('UNKNOWN','SYNTHETIC','USER_INPUT','USER_ESTIMATE')),
 ADD CHECK ((untracked_performance_won IS NULL)=(untracked_source='UNKNOWN')),
 ADD CHECK ((current_qualified IS NULL)=(qualification_source='UNKNOWN')),
 ADD CHECK ((untracked_source IN ('USER_INPUT','USER_ESTIMATE'))=(untracked_as_of IS NOT NULL)),
 ADD CHECK ((qualification_source IN ('USER_INPUT','USER_ESTIMATE'))=(qualification_as_of IS NOT NULL));
