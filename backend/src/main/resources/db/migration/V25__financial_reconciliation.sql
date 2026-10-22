CREATE TABLE financial_connection_checks (
 user_id uuid NOT NULL,
 request_id uuid NOT NULL,
 connection_id uuid NOT NULL,
 expected_version bigint NOT NULL,
 result jsonb NOT NULL,
 checked_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,request_id),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE
);
