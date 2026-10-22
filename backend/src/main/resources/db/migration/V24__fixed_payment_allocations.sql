ALTER TABLE fixed_cost_links ADD COLUMN allocated_won bigint CHECK(allocated_won>0);
ALTER TABLE fixed_cost_links DROP CONSTRAINT fixed_cost_links_pkey;
ALTER TABLE fixed_cost_links ADD PRIMARY KEY(user_id,transaction_id,plan_id,month);
