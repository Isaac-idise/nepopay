-- NepOpay core schema. Money is BIGINT kobo. The database enforces the rules, not just the Java code.

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(20)  NOT NULL,
    first_name          VARCHAR(50)  NOT NULL,
    last_name           VARCHAR(50)  NOT NULL,
    email               VARCHAR(254) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    pin_hash            VARCHAR(100) NOT NULL,
    pin_failed_attempts INT          NOT NULL DEFAULT 0,
    pin_locked_until    TIMESTAMPTZ,
    role                VARCHAR(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status              VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email    ON users (lower(email));
CREATE UNIQUE INDEX ux_users_phone    ON users (phone);

CREATE TABLE accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID REFERENCES users (id),
    account_number VARCHAR(10) NOT NULL UNIQUE,
    account_type   VARCHAR(20) NOT NULL CHECK (account_type IN ('USER', 'SYSTEM_FUNDING', 'SYSTEM_HOLD')),
    balance_kobo   BIGINT      NOT NULL DEFAULT 0,
    currency       CHAR(3)     NOT NULL DEFAULT 'NGN',
    status         VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A real customer account can never be overdrawn. System accounts may go negative by design.
    CONSTRAINT ck_user_balance_non_negative CHECK (account_type <> 'USER' OR balance_kobo >= 0),
    CONSTRAINT ck_user_account_has_owner    CHECK (account_type <> 'USER' OR user_id IS NOT NULL)
);
CREATE INDEX ix_accounts_user ON accounts (user_id);

CREATE TABLE transactions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference              VARCHAR(40) NOT NULL UNIQUE,
    sender_account_id      UUID        NOT NULL REFERENCES accounts (id),
    receiver_account_id    UUID        NOT NULL REFERENCES accounts (id),
    amount_kobo            BIGINT      NOT NULL CHECK (amount_kobo > 0),
    type                   VARCHAR(30) NOT NULL CHECK (type IN
                           ('P2P_TRANSFER', 'DEMO_WELCOME_CREDIT', 'DEMO_RESET', 'REVERSAL', 'GATEWAY_PAYMENT')),
    status                 VARCHAR(12) NOT NULL CHECK (status IN
                           ('PENDING', 'PROCESSING', 'SUCCESSFUL', 'FAILED', 'REVERSED', 'FLAGGED')),
    description            VARCHAR(140),
    risk_score             INT         NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    failure_reason         VARCHAR(100),
    related_transaction_id UUID REFERENCES transactions (id),
    archived               BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sender_not_receiver CHECK (sender_account_id <> receiver_account_id)
);
CREATE INDEX ix_tx_sender   ON transactions (sender_account_id, created_at DESC);
CREATE INDEX ix_tx_receiver ON transactions (receiver_account_id, created_at DESC);

CREATE TABLE ledger_entries (
    id                  BIGSERIAL PRIMARY KEY,
    transaction_id      UUID        NOT NULL REFERENCES transactions (id),
    account_id          UUID        NOT NULL REFERENCES accounts (id),
    entry_type          VARCHAR(6)  NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount_kobo         BIGINT      NOT NULL CHECK (amount_kobo > 0),
    balance_after_kobo  BIGINT      NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ledger_account ON ledger_entries (account_id, created_at DESC);
CREATE INDEX ix_ledger_tx      ON ledger_entries (transaction_id);

-- Ledger entries are append-only: corrections are new entries, never edits or deletes.
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_no_update_delete
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();
