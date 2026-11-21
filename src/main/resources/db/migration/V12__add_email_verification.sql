-- #71: an account counts only once its email address is verified. Accounts that already exist count as verified.
ALTER TABLE accounts ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN verification_token_hash VARCHAR(64);
ALTER TABLE accounts ADD COLUMN verification_token_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE accounts SET email_verified_at = created_at;

-- Only the SHA-256 of a token is stored, with its expiry; both are cleared once it is used.
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_verification_token
    CHECK ((verification_token_hash IS NULL) = (verification_token_expires_at IS NULL));
CREATE UNIQUE INDEX uk_accounts_verification_token ON accounts(verification_token_hash);
