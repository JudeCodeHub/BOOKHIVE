-- Library-wide rules that admins can change without a release (checklist step 215, ADM-1).
-- One row per group; the value is that group's settings as JSON. The application reads each
-- group into a typed record (SettingsService), so a typo in a key can't silently do nothing.
--
-- Defaults from PRD §4. Per-membership-type limits (items, loan days, renewals, holds) stay in
-- membership_types; these are the rules that apply to everyone.

CREATE TABLE library_settings (
    key        VARCHAR(50) PRIMARY KEY,
    value      JSONB       NOT NULL,
    updated_by UUID        REFERENCES users (id),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_library_settings_key CHECK (key IN ('fines', 'holds', 'membership', 'notifications')),
    CONSTRAINT ck_library_settings_object CHECK (jsonb_typeof(value) = 'object')
);

INSERT INTO library_settings (key, value) VALUES
    -- Amounts in LKR (PRD §4.4)
    ('fines', '{
        "perDay": 5.00,
        "graceDays": 1,
        "maxPerItem": 300.00,
        "lostDefaultPrice": 1500.00,
        "lostProcessingFee": 200.00,
        "blockBorrowingAt": 500.00,
        "librarianWaiveLimit": 300.00
    }'),
    -- PRD §4.3
    ('holds', '{
        "pickupOpenDays": 3
    }'),
    -- PRD §4.1 and §4.5
    ('membership', '{
        "validityYears": 1,
        "renewalWindowDays": 30,
        "documentRetentionDays": 90,
        "historyAnonymiseDays": 30
    }'),
    -- NOTIF-4: when reminders go out
    ('notifications', '{
        "dueSoonDaysBefore": 2,
        "holdExpiringDaysBefore": 1,
        "membershipExpiringDaysBefore": 30
    }');
