ALTER TABLE financial_transactions DROP CONSTRAINT financial_transactions_card_mask_check;
ALTER TABLE financial_transactions ADD CONSTRAINT financial_transactions_card_mask_check CHECK(card_mask IS NULL OR card_mask ~ '^([0-9]{4}[*]{8}[0-9]{4}|[*]{13}[0-9]{3})$');
