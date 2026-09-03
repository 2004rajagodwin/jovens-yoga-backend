-- Closes the trial-detail IDOR: public reads/cancels of a trial by its sequential
-- numeric id are replaced with a cryptographically random, short-lived access token.
-- Only a SHA-256 hash of the token is stored — the raw token is never persisted.
ALTER TABLE trials
    ADD COLUMN access_token_hash VARCHAR(64) NULL,
    ADD COLUMN access_token_expires_at DATETIME NULL;

CREATE INDEX idx_trials_access_token_hash ON trials (access_token_hash);
