-- Membership types (PRD §4.1): the borrowing rules per type are data, not code, so admins can
-- change them (checklist step 145) without a new release.

CREATE TABLE membership_types (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Stable key used in code and URLs: ADULT, JUNIOR, SENIOR, STUDENT
    code              VARCHAR(30)   NOT NULL,
    -- Shown to users in their language
    name_en           VARCHAR(60)   NOT NULL,
    name_si           VARCHAR(60)   NOT NULL,
    name_ta           VARCHAR(60)   NOT NULL,

    -- Age on the application date; NULL = no limit on that side
    min_age           INTEGER,
    max_age           INTEGER,

    max_items         INTEGER       NOT NULL,
    loan_days         INTEGER       NOT NULL,
    max_renewals      INTEGER       NOT NULL,
    max_holds         INTEGER       NOT NULL,
    -- LKR per year; 0 = free
    annual_fee        NUMERIC(10,2) NOT NULL DEFAULT 0,

    -- Document the applicant must upload: NIC, STUDENT_ID or NONE
    required_document VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    -- Junior members need an active adult member as guardian (MEM-2)
    requires_guardian BOOLEAN       NOT NULL DEFAULT false,
    -- Library sections this type may borrow from; NULL = every section
    allowed_sections  TEXT[],

    -- Retired types stay for existing members but can't be chosen for new applications
    active            BOOLEAN       NOT NULL DEFAULT true,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_membership_types_code UNIQUE (code),
    CONSTRAINT ck_membership_types_ages CHECK (
        (min_age IS NULL OR min_age >= 0)
        AND (max_age IS NULL OR max_age >= 0)
        AND (min_age IS NULL OR max_age IS NULL OR min_age <= max_age)),
    CONSTRAINT ck_membership_types_limits CHECK (
        max_items >= 0 AND loan_days > 0 AND max_renewals >= 0 AND max_holds >= 0),
    CONSTRAINT ck_membership_types_fee CHECK (annual_fee >= 0),
    CONSTRAINT ck_membership_types_document CHECK (required_document IN ('NIC', 'STUDENT_ID', 'NONE'))
);
