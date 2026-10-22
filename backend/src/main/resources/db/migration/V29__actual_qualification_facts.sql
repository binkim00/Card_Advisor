CREATE TABLE actual_qualification_facts (
 user_id uuid NOT NULL, card_id uuid NOT NULL, month date NOT NULL,
 previous_performance_won bigint CHECK(previous_performance_won BETWEEN -100000000 AND 100000000),
 previous_source text NOT NULL CHECK(previous_source IN ('UNKNOWN','USER_INPUT','USER_ESTIMATE')),
 as_of date, first_use_date date, version bigint NOT NULL CHECK(version>0),
 qualification_version bigint NOT NULL, rule_version text NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,card_id,month),FOREIGN KEY(user_id,card_id) REFERENCES cards(user_id,id) ON DELETE CASCADE,
 CHECK((previous_performance_won IS NULL)=(previous_source='UNKNOWN')),
 CHECK((previous_performance_won IS NULL)=(as_of IS NULL)),CHECK(EXTRACT(DAY FROM month)=1)
);
