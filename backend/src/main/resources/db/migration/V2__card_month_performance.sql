-- Preserve V1 and legacy unlinked transactions; never guess their card or approval amount.
CREATE TABLE card_month_states (
 user_id uuid NOT NULL, card_id uuid NOT NULL, month date NOT NULL,
 untracked_performance_won bigint CHECK(untracked_performance_won>=0),
 current_qualified boolean, remaining_cap_won bigint CHECK(remaining_cap_won>=0),
 version bigint NOT NULL CHECK(version>0), source text NOT NULL CHECK(source='SYNTHETIC'),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,card_id,month),
 CHECK(extract(day from month)=1),
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE
);
ALTER TABLE ledger_entries ADD COLUMN card_id uuid;
ALTER TABLE ledger_entries ADD COLUMN card_charge_won bigint;
ALTER TABLE ledger_entries ADD COLUMN performance_eligible boolean;
ALTER TABLE ledger_entries ADD COLUMN performance_rule_version text;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_card_owner
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_card_amount
 CHECK((card_id IS NULL AND card_charge_won IS NULL) OR
       (card_id IS NOT NULL AND card_charge_won IS NOT NULL AND card_charge_won>=0 AND card_charge_won<=amount_won));
ALTER TABLE cancellations ADD COLUMN card_refund_won bigint
 CHECK(card_refund_won>=0 AND card_refund_won<=amount_won);
CREATE INDEX ledger_user_card_date ON ledger_entries(user_id,card_id,purchased_at);
