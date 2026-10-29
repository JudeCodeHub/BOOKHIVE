-- Stock-take (RPT-6, checklist step 365): staff scan every copy on a shelf; copies that should be
-- there but weren't scanned are missing. Which copies "should be there" is worked out when the
-- results are viewed (AVAILABLE copies in the session's scope), so a book lent during the
-- stock-take isn't reported missing.

CREATE TABLE stocktake_sessions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(120) NOT NULL,
    -- Scope: a category and everything under it, and/or shelves whose location starts with this
    -- (as whole words: 'Non-fiction 500s, shelf 1' doesn't include 'shelf 11').
    -- Both NULL = the whole library. No foreign key: the history stays if a category goes.
    category_id    UUID,
    shelf_prefix   VARCHAR(100),
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    started_by     UUID         NOT NULL REFERENCES users (id),
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_by    UUID         REFERENCES users (id),
    finished_at    TIMESTAMPTZ,
    -- Frozen when the session is completed, so the result can be read later
    expected_count INTEGER,
    found_count    INTEGER,
    missing_count  INTEGER,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_stocktake_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_stocktake_finished CHECK ((status = 'OPEN') = (finished_at IS NULL))
);

CREATE INDEX ix_stocktake_sessions_started ON stocktake_sessions (started_at DESC);

-- Every barcode scanned in a session, with what the scan found
CREATE TABLE stocktake_scans (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES stocktake_sessions (id) ON DELETE CASCADE,
    barcode    VARCHAR(30) NOT NULL,
    -- NULL when the barcode isn't in the catalog
    copy_id    UUID        REFERENCES copies (id),
    result     VARCHAR(20) NOT NULL,
    -- The copy's status when scanned (e.g. ON_LOAN for a book that shouldn't be on the shelf)
    copy_status VARCHAR(20),
    scanned_by UUID        NOT NULL REFERENCES users (id),
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_stocktake_result
        CHECK (result IN ('FOUND', 'NOT_IN_SCOPE', 'NOT_ON_SHELF', 'UNKNOWN'))
);

-- A copy counts once per session (scanning it again is reported, not stored)
CREATE UNIQUE INDEX uk_stocktake_scans_copy ON stocktake_scans (session_id, copy_id)
    WHERE copy_id IS NOT NULL;
CREATE INDEX ix_stocktake_scans_session ON stocktake_scans (session_id, scanned_at DESC);
