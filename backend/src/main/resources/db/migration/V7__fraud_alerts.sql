CREATE TABLE fraud_alerts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID        NOT NULL UNIQUE REFERENCES transactions (id),
    risk_score     INT         NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_level     VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    reasons        JSONB       NOT NULL,                  -- which rules fired, with points and a readable reason
    status         VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'INFO')),
    reviewed_by    UUID REFERENCES users (id),
    reviewed_at    TIMESTAMPTZ,
    review_note    VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- OPEN = held, waiting for an admin. INFO = medium risk: processed normally, logged for monitoring.
CREATE INDEX ix_fraud_alerts_status ON fraud_alerts (status, created_at DESC);
