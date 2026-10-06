CREATE TABLE beneficiaries (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID        NOT NULL REFERENCES users (id),
    beneficiary_account_id UUID        NOT NULL REFERENCES accounts (id),
    nickname               VARCHAR(30),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_beneficiary UNIQUE (user_id, beneficiary_account_id)
);
CREATE INDEX ix_beneficiaries_user ON beneficiaries (user_id);

-- Demo reset hides OLD history for one account only. A shared "archived" flag on the transaction row
-- would also hide it from the other person, whose history must not change.
ALTER TABLE accounts ADD COLUMN history_reset_at TIMESTAMPTZ;
