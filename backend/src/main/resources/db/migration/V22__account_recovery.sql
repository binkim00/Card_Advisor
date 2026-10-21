ALTER TABLE app_users ADD COLUMN email_verified_at timestamptz;
CREATE TABLE account_action_tokens (
 token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 purpose text NOT NULL CHECK(purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
 expires_at timestamptz NOT NULL, consumed_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX account_action_owner ON account_action_tokens(user_id,purpose);
