-- Recommendations created before ledger benefit accounting used the unconsumed base cap.
-- Preserve their history but invalidate them for users with linked transactions.
UPDATE app_users u SET revision=revision+1
WHERE EXISTS(SELECT 1 FROM ledger_entries e WHERE e.user_id=u.id AND e.card_id IS NOT NULL);
