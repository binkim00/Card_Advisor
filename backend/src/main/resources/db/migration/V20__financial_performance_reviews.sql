CREATE TABLE financial_performance_reviews (
 user_id uuid NOT NULL, transaction_id uuid NOT NULL, card_id uuid NOT NULL,
 mapping_version bigint NOT NULL, transaction_fingerprint bytea NOT NULL,
 refund_signature text NOT NULL, rule_version text NOT NULL,
 decision text NOT NULL CHECK(decision IN ('INCLUDE','EXCLUDE','UNKNOWN')),
 version bigint NOT NULL CHECK(version>0), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,transaction_id),
 FOREIGN KEY(user_id,transaction_id) REFERENCES financial_transactions(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE
);
CREATE TABLE financial_performance_refund_dates (
 user_id uuid NOT NULL, transaction_id uuid NOT NULL, cancellation_id uuid NOT NULL,
 receipt_date date,
 PRIMARY KEY(user_id,transaction_id,cancellation_id),
 FOREIGN KEY(user_id,transaction_id) REFERENCES financial_performance_reviews(user_id,transaction_id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,cancellation_id) REFERENCES cancellations(user_id,id) ON DELETE CASCADE
);
