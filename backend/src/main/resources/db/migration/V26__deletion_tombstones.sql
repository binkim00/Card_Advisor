-- No email, credential, financial details, or foreign key to a deleted account.
-- Keep this minimal registry until all backups containing the account have expired.
CREATE TABLE deleted_user_tombstones (
 user_id uuid PRIMARY KEY,
 deleted_at timestamptz NOT NULL DEFAULT now()
);
