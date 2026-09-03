-- Admin login moves from email-based to username-based authentication.
-- Safe/additive migration: adds columns, backfills existing rows, then tightens
-- constraints. Does not drop or truncate any table, and preserves existing admin rows.

ALTER TABLE admins ADD COLUMN username VARCHAR(100) NULL AFTER name;
ALTER TABLE admins ADD COLUMN last_login_at DATETIME NULL;

-- Backfill any pre-existing admin rows (seeded by email in an earlier phase) with a
-- best-effort username derived from their email, so the NOT NULL constraint below is safe.
UPDATE admins SET username = SUBSTRING_INDEX(email, '@', 1) WHERE username IS NULL;

ALTER TABLE admins MODIFY COLUMN username VARCHAR(100) NOT NULL;
ALTER TABLE admins ADD CONSTRAINT uq_admins_username UNIQUE (username);

-- Email is no longer the login identifier; keep the column for optional contact info.
ALTER TABLE admins MODIFY COLUMN email VARCHAR(255) NULL;
