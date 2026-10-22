ALTER TABLE ledger_entries ADD COLUMN budget_allocations jsonb;
CREATE TABLE ledger_classification_history (
 id bigserial PRIMARY KEY,
 user_id uuid NOT NULL,
 transaction_id uuid NOT NULL,
 version bigint NOT NULL,
 category varchar(30) NOT NULL,
 included boolean NOT NULL,
 allocations jsonb,
 changed_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,transaction_id,version),
 FOREIGN KEY(user_id,transaction_id) REFERENCES ledger_entries(user_id,id) ON DELETE CASCADE
);
