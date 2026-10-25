-- Physical items (checklist step 152, ADR 0003). Every copy belongs to one book; loans point to
-- copies, so this is what the desk scans.

CREATE TABLE copies (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id            UUID          NOT NULL REFERENCES books (id),

    -- Printed on the label and scanned at the desk (generated in step 160, e.g. 'C00012345')
    barcode            VARCHAR(30)   NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    -- CAT-7: REFERENCE_ONLY never leaves the library; RESTRICTED needs staff approval
    lending_type       VARCHAR(20)   NOT NULL DEFAULT 'LENDABLE',
    condition          VARCHAR(20)   NOT NULL DEFAULT 'GOOD',
    -- Where to find it, e.g. 'Adult fiction, shelf 12'
    shelf_location     VARCHAR(100),

    acquired_at        DATE,
    acquisition_source VARCHAR(20)   NOT NULL DEFAULT 'PURCHASE',
    -- What the library paid; also the default replacement cost if it's lost (PRD §4.4)
    price              NUMERIC(10,2),
    notes              VARCHAR(500),

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_copies_barcode UNIQUE (barcode),
    CONSTRAINT ck_copies_barcode CHECK (barcode ~ '^[A-Z0-9-]{3,30}$'),
    CONSTRAINT ck_copies_status CHECK (status IN (
        'AVAILABLE', 'ON_LOAN', 'ON_HOLD_SHELF', 'IN_TRANSIT', 'IN_REPAIR', 'LOST', 'WITHDRAWN')),
    CONSTRAINT ck_copies_lending_type
        CHECK (lending_type IN ('LENDABLE', 'REFERENCE_ONLY', 'RESTRICTED')),
    CONSTRAINT ck_copies_condition CHECK (condition IN ('NEW', 'GOOD', 'FAIR', 'POOR', 'DAMAGED')),
    CONSTRAINT ck_copies_source CHECK (acquisition_source IN ('PURCHASE', 'DONATION', 'OTHER')),
    CONSTRAINT ck_copies_price CHECK (price >= 0)
);

-- Availability per title ("2 of 3 available") is counted from here, never stored on books
CREATE INDEX ix_copies_book_status ON copies (book_id, status);
