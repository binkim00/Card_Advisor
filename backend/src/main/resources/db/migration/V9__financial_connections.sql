CREATE TABLE financial_connections (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 environment text NOT NULL CHECK(environment IN ('SANDBOX','DEMO','PRODUCTION')),
 provider_scope text NOT NULL,
 organization text NOT NULL CHECK(organization ~ '^[0-9]{4}$'),
 state text NOT NULL CHECK(state IN ('CONNECTING','ACTIVE','REAUTHENTICATING','DISCONNECTING','AUTH_REQUIRED','UNKNOWN','FAILED','DISCONNECTED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>0),
 key_id text, cid_nonce bytea, cid_ciphertext bytea, cid_hash bytea,
 consent_version text NOT NULL, consented_at timestamptz NOT NULL DEFAULT now(),
 last_operation_id uuid, last_error text,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,id), UNIQUE(user_id,environment,provider_scope,organization),
 UNIQUE(environment,provider_scope,cid_hash),
 CHECK ((key_id IS NULL AND cid_nonce IS NULL AND cid_ciphertext IS NULL AND cid_hash IS NULL)
  OR (key_id IS NOT NULL AND cid_nonce IS NOT NULL AND cid_ciphertext IS NOT NULL AND cid_hash IS NOT NULL
   AND octet_length(cid_nonce)=12 AND octet_length(cid_ciphertext)>=16 AND octet_length(cid_hash)=32)),
 CHECK (state <> 'ACTIVE' OR cid_ciphertext IS NOT NULL),
 CHECK (state <> 'DISCONNECTED' OR cid_ciphertext IS NULL)
);
CREATE TABLE financial_connection_operations (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, connection_id uuid NOT NULL,
 idempotency_key uuid NOT NULL, kind text NOT NULL CHECK(kind IN ('CREATE','REAUTHORIZE','DISCONNECT')),
 fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32), fingerprint_key_id text NOT NULL,
 status text NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED','UNKNOWN','AUTH_REQUIRED')),
 previous_state text NOT NULL, error_code text,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,idempotency_key), UNIQUE(user_id,connection_id,id),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE
);
ALTER TABLE financial_connections ADD CONSTRAINT financial_connection_last_operation_fk
 FOREIGN KEY(user_id,id,last_operation_id) REFERENCES financial_connection_operations(user_id,connection_id,id)
 DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX financial_connections_owner ON financial_connections(user_id,updated_at);
CREATE INDEX financial_operation_pending ON financial_connection_operations(updated_at) WHERE status='PENDING';
