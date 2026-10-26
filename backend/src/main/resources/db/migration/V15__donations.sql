-- Donations log (CAT-10, checklist step 169): who gave books and when. Each donated copy points
-- to its donation, so staff can see (and thank donors for) exactly what came from whom.

CREATE TABLE donations (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_name    VARCHAR(200) NOT NULL,
    -- Phone or email, for a thank-you letter; optional (some donors stay anonymous)
    donor_contact VARCHAR(200),
    received_at   DATE         NOT NULL,
    notes         VARCHAR(1000),
    -- The staff member who logged it
    recorded_by   UUID         REFERENCES users (id),

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_donations_donor CHECK (btrim(donor_name) <> '')
);

CREATE INDEX ix_donations_received ON donations (received_at DESC);
-- Find past donations by the same person
CREATE INDEX ix_donations_donor_trgm ON donations USING gin (donor_name gin_trgm_ops);

ALTER TABLE copies ADD COLUMN donation_id UUID REFERENCES donations (id);
CREATE INDEX ix_copies_donation ON copies (donation_id) WHERE donation_id IS NOT NULL;
