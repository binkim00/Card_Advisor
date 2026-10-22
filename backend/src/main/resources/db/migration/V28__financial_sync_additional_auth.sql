ALTER TABLE financial_sync_runs DROP CONSTRAINT financial_sync_runs_status_check;
ALTER TABLE financial_sync_runs ADD CONSTRAINT financial_sync_runs_status_check CHECK(status IN ('RUNNING','IMPORTED','PARTIAL','FAILED','AUTH_REQUIRED','AUTH_PENDING','AUTH_UNKNOWN'));
CREATE TABLE financial_sync_auth (
 user_id uuid NOT NULL, run_id uuid NOT NULL, version bigint NOT NULL CHECK(version>0),
 method text NOT NULL CHECK(method IN ('smsAuthNo','emailAuthNo','simpleAuth')),
 key_id text, nonce bytea, ciphertext bytea, expires_at timestamptz NOT NULL,
 last_request_key uuid, PRIMARY KEY(user_id,run_id),
 FOREIGN KEY(user_id,run_id) REFERENCES financial_sync_runs(user_id,id) ON DELETE CASCADE,
 CHECK((key_id IS NULL AND nonce IS NULL AND ciphertext IS NULL) OR (key_id IS NOT NULL AND octet_length(nonce)=12 AND ciphertext IS NOT NULL))
);
DROP INDEX financial_sync_one_running;
CREATE UNIQUE INDEX financial_sync_one_running ON financial_sync_runs(user_id,connection_id) WHERE status IN ('RUNNING','AUTH_REQUIRED','AUTH_PENDING','AUTH_UNKNOWN');
