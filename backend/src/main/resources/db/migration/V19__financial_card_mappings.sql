CREATE TABLE financial_card_mappings (
 user_id uuid NOT NULL, connection_id uuid NOT NULL,
 source_key varchar(64) NOT NULL CHECK(source_key ~ '^[a-f0-9]{64}$'),
 source_mask text NOT NULL CHECK(source_mask ~ '^([0-9]{4}[*]{8}[0-9]{4}|[*]{13}[0-9]{3})$'),
 source_label varchar(120) NOT NULL, card_id uuid,
 version bigint NOT NULL CHECK(version>0), confirmed_at timestamptz NOT NULL,
 PRIMARY KEY(user_id,connection_id,source_key),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE
);
