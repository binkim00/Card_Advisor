CREATE TABLE financial_held_cards (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL,
 connection_id uuid NOT NULL,
 source_key varchar(64) NOT NULL,
 display_name varchar(120) NOT NULL,
 masked_number varchar(24) NOT NULL,
 card_type varchar(40) NOT NULL,
 current boolean NOT NULL DEFAULT true,
 version bigint NOT NULL DEFAULT 1 CHECK(version>0),
 card_id uuid,
 fetched_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,connection_id,source_key),
 FOREIGN KEY(user_id,connection_id) REFERENCES financial_connections(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE,
 CHECK(masked_number ~ '^(\*{12}[0-9]{4}|\*{13}[0-9]{3})$')
);
CREATE INDEX held_cards_owner ON financial_held_cards(user_id,connection_id);
