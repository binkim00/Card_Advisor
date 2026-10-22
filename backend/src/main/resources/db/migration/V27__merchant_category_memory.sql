CREATE TABLE merchant_category_rules (
 user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 merchant_key char(64) NOT NULL,
 category varchar(32),
 version bigint NOT NULL CHECK(version > 0),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id, merchant_key)
);
