CREATE TABLE fixed_cost_plans (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 version bigint NOT NULL CHECK(version>0),
 UNIQUE(user_id,id)
);
CREATE TABLE fixed_cost_rules (
 user_id uuid NOT NULL,
 plan_id uuid NOT NULL,
 effective_month date NOT NULL CHECK(extract(day FROM effective_month)=1),
 name varchar(80) NOT NULL,
 amount_won bigint NOT NULL CHECK(amount_won BETWEEN 1 AND 100000000),
 category varchar(30) NOT NULL,
 day_of_month integer NOT NULL CHECK(day_of_month BETWEEN 1 AND 31),
 active boolean NOT NULL,
 PRIMARY KEY(user_id,plan_id,effective_month),
 FOREIGN KEY(user_id,plan_id) REFERENCES fixed_cost_plans(user_id,id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ledger_entries_user_id_id ON ledger_entries(user_id,id);
CREATE TABLE fixed_cost_links (
 user_id uuid NOT NULL,
 plan_id uuid NOT NULL,
 month date NOT NULL CHECK(extract(day FROM month)=1),
 transaction_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,transaction_id),
 FOREIGN KEY(user_id,plan_id) REFERENCES fixed_cost_plans(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,transaction_id) REFERENCES ledger_entries(user_id,id) ON DELETE CASCADE
);
CREATE INDEX fixed_cost_links_month ON fixed_cost_links(user_id,month,plan_id);
