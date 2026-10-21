CREATE TABLE financial_benefit_reviews (
 user_id uuid NOT NULL, transaction_id uuid NOT NULL, card_id uuid NOT NULL,
 evidence_hash text NOT NULL, decision text NOT NULL CHECK(decision IN ('RULE','EXCLUDE','UNKNOWN')),
 area text, version bigint NOT NULL CHECK(version>0), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,transaction_id),
 FOREIGN KEY(user_id,transaction_id) REFERENCES financial_transactions(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE
);
CREATE TABLE financial_benefit_months (
 user_id uuid NOT NULL, card_id uuid NOT NULL, month date NOT NULL,
 tier integer CHECK(tier BETWEEN 0 AND 3), coverage_confirmed boolean NOT NULL,
 qualification_version bigint NOT NULL, version bigint NOT NULL CHECK(version>0), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,card_id,month),
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE
);
