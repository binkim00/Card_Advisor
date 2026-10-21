CREATE TABLE user_preferences (
 user_id uuid PRIMARY KEY REFERENCES app_users ON DELETE CASCADE,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0));
CREATE TABLE user_consents (
 user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 purpose text NOT NULL CHECK(purpose IN ('AI','CHECKOUT_DETECTION')),
 accepted boolean NOT NULL, policy_version text NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,purpose));
CREATE TABLE consent_events (
 id bigserial PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 purpose text NOT NULL, accepted boolean NOT NULL, policy_version text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE refresh_sessions (
 token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_users ON DELETE CASCADE,
 family_id uuid NOT NULL, access_token_hash text NOT NULL,
 expires_at timestamptz NOT NULL, used_at timestamptz, revoked_at timestamptz);
CREATE INDEX refresh_family ON refresh_sessions(family_id);
CREATE TABLE request_limits (
 bucket_hash text PRIMARY KEY, attempts integer NOT NULL, expires_at timestamptz NOT NULL);
CREATE INDEX request_limits_expiry ON request_limits(expires_at);
