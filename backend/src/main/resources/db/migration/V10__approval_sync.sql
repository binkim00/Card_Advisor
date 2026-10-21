ALTER TABLE financial_connections ADD COLUMN transaction_key_id text;
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_source_check;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_source_check CHECK(source IN ('MANUAL','CODEF'));

CREATE TABLE financial_sync_runs (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, connection_id uuid NOT NULL,
 idempotency_key uuid NOT NULL, from_date date NOT NULL, to_date date NOT NULL,
 connection_version bigint NOT NULL, status text NOT NULL CHECK(status IN ('RUNNING','IMPORTED','PARTIAL','FAILED')),
 received integer NOT NULL DEFAULT 0, applied integer NOT NULL DEFAULT 0, held integer NOT NULL DEFAULT 0,
 error_code text, created_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz,
 UNIQUE(user_id,idempotency_key), UNIQUE(user_id,id),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX financial_sync_one_running ON financial_sync_runs(user_id,connection_id) WHERE status='RUNNING';
CREATE TABLE financial_transactions (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, connection_id uuid NOT NULL,
 identity_hash bytea NOT NULL CHECK(octet_length(identity_hash)=32), fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),
 card_mask text, used_date date, used_time text, kind text NOT NULL CHECK(kind IN ('APPROVAL','CANCEL_EVENT','REJECTED')),
 amount_won bigint, cancelled_won bigint, currency text, source_amount text, payment_type text, installments integer,
 status text NOT NULL, issue text, version bigint NOT NULL DEFAULT 1,
 ledger_id uuid, linked_transaction_id uuid, manual_link boolean NOT NULL DEFAULT false,
 rule_version text NOT NULL, source text NOT NULL CHECK(source IN ('SYNTHETIC','PROVIDER_REPORTED')),
 last_run_id uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,id), UNIQUE(user_id,connection_id,identity_hash),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,last_run_id) REFERENCES financial_sync_runs(user_id,id),
 FOREIGN KEY(user_id,ledger_id) REFERENCES ledger_entries(user_id,id),
 FOREIGN KEY(user_id,linked_transaction_id) REFERENCES financial_transactions(user_id,id),
 CHECK(card_mask IS NULL OR card_mask ~ '^[0-9]{4}[*]{8}[0-9]{4}$')
);
CREATE UNIQUE INDEX financial_transaction_ledger ON financial_transactions(user_id,ledger_id) WHERE ledger_id IS NOT NULL;
ALTER TABLE cancellations ADD CONSTRAINT cancellation_owner_id UNIQUE(user_id,id);
CREATE TABLE financial_refund_applications (
 user_id uuid NOT NULL, financial_transaction_id uuid NOT NULL, cancellation_id uuid NOT NULL,
 cumulative_won bigint NOT NULL CHECK(cumulative_won>0), PRIMARY KEY(user_id,financial_transaction_id,cumulative_won),
 FOREIGN KEY(user_id,financial_transaction_id) REFERENCES financial_transactions(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,cancellation_id) REFERENCES cancellations(user_id,id) ON DELETE CASCADE
);
