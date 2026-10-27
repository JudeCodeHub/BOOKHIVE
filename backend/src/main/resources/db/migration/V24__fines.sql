-- Fines (PRD §4.4, FIN-3, checklist step 269). A fine is one reason a member owes money (a late
-- return, a lost or damaged book, a membership fee); what happened to it is a LEDGER of
-- transactions that are only ever added, never edited or deleted:
--
--   CHARGE        + money owed (overdue fines add one line per day while the book is out)
--   PAYMENT       - paid (cash at the desk or online)
--   WAIVER        - let off by staff, with a reason (role limits apply)
--   CANCELLATION  - charge withdrawn (e.g. a "lost" book turned up)
--   REFUND        + money given back after a cancellation of something already paid
--
-- Balance = charges - payments - waivers - cancellations + refunds. History is always auditable.

CREATE TABLE fines (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   UUID         NOT NULL REFERENCES members (id),
    -- The loan it comes from (overdue, lost, damage); NULL for membership fees
    loan_id     UUID         REFERENCES loans (id),
    type        VARCHAR(20)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    -- OPEN: something to pay (or still growing); SETTLED: paid/waived to zero; CANCELLED
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    -- Overdue fines grow daily until the book comes back (step 271); then fixed (step 272)
    accruing    BOOLEAN      NOT NULL DEFAULT false,
    settled_at  TIMESTAMPTZ,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_fines_type CHECK (type IN ('OVERDUE', 'LOST', 'DAMAGE', 'MEMBERSHIP_FEE')),
    CONSTRAINT ck_fines_status CHECK (status IN ('OPEN', 'SETTLED', 'CANCELLED')),
    CONSTRAINT ck_fines_loan CHECK (type = 'MEMBERSHIP_FEE' OR loan_id IS NOT NULL),
    CONSTRAINT ck_fines_accruing CHECK (NOT accruing OR (type = 'OVERDUE' AND status = 'OPEN'))
);

-- One overdue fine per loan, grown day by day; likewise one lost charge per loan
CREATE UNIQUE INDEX uk_fines_loan_overdue ON fines (loan_id) WHERE type = 'OVERDUE';
CREATE UNIQUE INDEX uk_fines_loan_lost ON fines (loan_id) WHERE type = 'LOST';
-- A member's fines (balance, borrowing block, fines page)
CREATE INDEX ix_fines_member_status ON fines (member_id, status);
-- Daily accrual job
CREATE INDEX ix_fines_accruing ON fines (loan_id) WHERE accruing;

CREATE TABLE fine_transactions (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    fine_id    UUID          NOT NULL REFERENCES fines (id),
    kind       VARCHAR(20)   NOT NULL,
    -- Always positive; the kind says which way it goes
    amount     NUMERIC(10,2) NOT NULL,
    -- PAYMENT/REFUND: CASH or ONLINE
    method     VARCHAR(20),
    -- Receipt number (cash) or payment order id (online)
    reference  VARCHAR(100),
    reason     VARCHAR(500),
    -- Staff or member who did it; NULL for automatic jobs
    actor_id   UUID          REFERENCES users (id),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_fine_tx_kind
        CHECK (kind IN ('CHARGE', 'PAYMENT', 'WAIVER', 'CANCELLATION', 'REFUND')),
    CONSTRAINT ck_fine_tx_amount CHECK (amount > 0),
    CONSTRAINT ck_fine_tx_method CHECK (method IS NULL OR method IN ('CASH', 'ONLINE')),
    CONSTRAINT ck_fine_tx_method_needed
        CHECK ((kind IN ('PAYMENT', 'REFUND')) = (method IS NOT NULL)),
    -- Letting someone off, withdrawing a charge or giving money back always needs a reason
    CONSTRAINT ck_fine_tx_reason
        CHECK (kind NOT IN ('WAIVER', 'CANCELLATION', 'REFUND') OR reason IS NOT NULL)
);

CREATE INDEX ix_fine_tx_fine ON fine_transactions (fine_id, created_at);
-- Cash receipt numbers are unique (one receipt book)
CREATE UNIQUE INDEX uk_fine_tx_cash_receipt
    ON fine_transactions (reference) WHERE method = 'CASH' AND kind = 'PAYMENT';

-- The ledger must stay true: no edits, no deletes, even from the application
CREATE FUNCTION fine_transactions_are_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fine_transactions is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fine_transactions_append_only
    BEFORE UPDATE OR DELETE ON fine_transactions
    FOR EACH ROW EXECUTE FUNCTION fine_transactions_are_append_only();
