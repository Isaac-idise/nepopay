-- Refresh-token sessions. Only a SHA-256 hash of the token is stored, never the token itself.
CREATE TABLE sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id),
    family_id   UUID         NOT NULL,   -- every token descended from one login shares a family
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX ix_sessions_user   ON sessions (user_id);
CREATE INDEX ix_sessions_family ON sessions (family_id);
