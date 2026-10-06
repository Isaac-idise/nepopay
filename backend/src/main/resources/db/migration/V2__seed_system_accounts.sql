-- System accounts: the issuer of demo money, and a holding account for flagged transfers.
-- Account numbers beginning 00 are reserved for system use; user accounts get prefix 99.
INSERT INTO accounts (account_number, account_type) VALUES
    ('0000000001', 'SYSTEM_FUNDING'),
    ('0000000002', 'SYSTEM_HOLD');
