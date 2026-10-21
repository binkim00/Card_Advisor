ALTER TABLE ledger_entries ADD COLUMN benefit_rate_bp integer CHECK(benefit_rate_bp BETWEEN 0 AND 10000);
ALTER TABLE ledger_entries ADD COLUMN benefit_rule_version text;
-- Card profiles are create-only. Backfill only previously supported synthetic linked rows.
-- Unlinked/unverified rows remain unknown; never infer a real product rule.
UPDATE ledger_entries e SET benefit_rate_bp=(c.payload->>'rateBp')::integer,
 benefit_rule_version='SYNTHETIC_BENEFIT_V1'
FROM cards c WHERE e.user_id=c.user_id AND e.card_id=c.id
 AND e.performance_rule_version='SYNTHETIC_V1' AND c.payload->>'source'='SYNTHETIC';
COMMENT ON COLUMN card_month_states.remaining_cap_won IS
 'Available benefit pool before linked ledger usage, not the recalculated remaining amount. Synthetic baseline only.';
