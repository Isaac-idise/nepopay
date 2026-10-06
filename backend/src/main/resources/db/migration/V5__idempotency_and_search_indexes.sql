-- One row per (user, client-generated key). Stores only the OUTCOME reference, not the response body.
CREATE TABLE idempotency_keys (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users (id),
    idem_key              VARCHAR(64) NOT NULL,
    request_hash          VARCHAR(64) NOT NULL,
    status                VARCHAR(12) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    transaction_reference VARCHAR(40),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_idempotency_user_key UNIQUE (user_id, idem_key)
);

-- Prefix searches (LIKE 'abc%') on lower-cased text.
CREATE INDEX ix_users_username_prefix  ON users (lower(username)   text_pattern_ops);
CREATE INDEX ix_users_firstname_prefix ON users (lower(first_name) text_pattern_ops);
CREATE INDEX ix_users_lastname_prefix  ON users (lower(last_name)  text_pattern_ops);
