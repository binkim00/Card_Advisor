CREATE TABLE app_users (
 id uuid PRIMARY KEY, email text UNIQUE NOT NULL, password_hash text NOT NULL,
 revision bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE sessions (
 token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 expires_at timestamptz NOT NULL);
CREATE TABLE cards (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 payload jsonb NOT NULL, UNIQUE(user_id,id));
CREATE TABLE budgets (
 user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE, month date NOT NULL,
 payload jsonb NOT NULL, version bigint NOT NULL, PRIMARY KEY(user_id,month));
CREATE TABLE ledger_entries (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 client_id uuid NOT NULL, amount_won bigint NOT NULL CHECK(amount_won>0),
 purchased_at date NOT NULL, category text NOT NULL, included boolean NOT NULL DEFAULT true,
 source text NOT NULL CHECK(source='MANUAL'), UNIQUE(user_id,client_id), UNIQUE(user_id,id));
CREATE TABLE cancellations (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, transaction_id uuid NOT NULL,
 client_id uuid NOT NULL, amount_won bigint NOT NULL CHECK(amount_won>0),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(user_id,client_id),
 FOREIGN KEY(user_id,transaction_id) REFERENCES ledger_entries(user_id,id) ON DELETE CASCADE);
CREATE TABLE checkouts (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 payload jsonb NOT NULL, version bigint NOT NULL, expires_at timestamptz NOT NULL, UNIQUE(user_id,id));
CREATE TABLE recommendations (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, checkout_id uuid NOT NULL,
 user_revision bigint NOT NULL, checkout_version bigint NOT NULL,
 payload jsonb NOT NULL, expires_at timestamptz NOT NULL,
 FOREIGN KEY(user_id,checkout_id) REFERENCES checkouts(user_id,id) ON DELETE CASCADE);
CREATE INDEX ledger_user_date ON ledger_entries(user_id,purchased_at,id);
CREATE INDEX sessions_expiry ON sessions(expires_at);

