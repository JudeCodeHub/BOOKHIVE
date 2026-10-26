-- Loans (checklist step 217): one row each time a copy goes out. Points to the copy and the
-- member by id (catalog and member modules); the PRD §4.2 rules live in the circulation service.

CREATE TABLE loans (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    copy_id         UUID        NOT NULL REFERENCES copies (id),
    -- NULL only after return, when the member's reading history is off: the loan is then
    -- anonymised (PRD §4.5), so statistics stay but nobody can see who read what
    member_id       UUID        REFERENCES members (id),

    checked_out_at  TIMESTAMPTZ NOT NULL,
    -- Calendar date in Sri Lanka, already moved off closed days and holidays (CIRC-5)
    due_date        DATE        NOT NULL,
    renew_count     SMALLINT    NOT NULL DEFAULT 0,
    last_renewed_at TIMESTAMPTZ,
    -- ACTIVE: out and on time. OVERDUE: past the due date (set by the nightly job).
    -- RETURNED: back. LOST: declared lost (charged, step 229); the copy is LOST too.
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    returned_at     TIMESTAMPTZ,

    checked_out_by  UUID        REFERENCES users (id),
    returned_by     UUID        REFERENCES users (id),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_loans_status CHECK (status IN ('ACTIVE', 'OVERDUE', 'RETURNED', 'LOST')),
    CONSTRAINT ck_loans_returned CHECK ((status = 'RETURNED') = (returned_at IS NOT NULL)),
    CONSTRAINT ck_loans_member_kept_while_open
        CHECK (member_id IS NOT NULL OR status = 'RETURNED'),
    CONSTRAINT ck_loans_renew_count CHECK (renew_count >= 0),
    CONSTRAINT ck_loans_due_after_checkout CHECK (due_date >= (checked_out_at AT TIME ZONE 'Asia/Colombo')::date)
);

-- The rule that matters most: a copy is on at most one open loan. Two desks scanning the same
-- book at the same moment can't both succeed (the second insert fails).
CREATE UNIQUE INDEX uk_loans_open_copy ON loans (copy_id) WHERE status IN ('ACTIVE', 'OVERDUE');

-- "My loans", loan limits, the desk's member summary
CREATE INDEX ix_loans_member_open ON loans (member_id) WHERE status IN ('ACTIVE', 'OVERDUE');
-- Nightly overdue job and the overdue list
CREATE INDEX ix_loans_due_open ON loans (due_date) WHERE status IN ('ACTIVE', 'OVERDUE');
-- Reading history (member) and copy history (staff)
CREATE INDEX ix_loans_member_history ON loans (member_id, checked_out_at DESC);
CREATE INDEX ix_loans_copy_history ON loans (copy_id, checked_out_at DESC);
