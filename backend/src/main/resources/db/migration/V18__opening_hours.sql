-- Opening hours and holidays (ADM-3, checklist step 205). Used for the "Open now" badge and the
-- visit page now, and for due dates later: a due date that falls on a closed day moves to the
-- next open day (CIRC-5). Admins edit both (checklist steps 407 and 420).

CREATE TABLE opening_hours (
    -- ISO weekday: 1 = Monday ... 7 = Sunday
    day_of_week SMALLINT    PRIMARY KEY,
    -- NULL times = closed all day
    opens_at    TIME,
    closes_at   TIME,

    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_opening_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_opening_hours_times CHECK (
        (opens_at IS NULL AND closes_at IS NULL)
        OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at < closes_at))
);

-- Defaults until the admin sets the real ones: Monday-Saturday 8:00-17:00, closed on Sunday
INSERT INTO opening_hours (day_of_week, opens_at, closes_at) VALUES
    (1, '08:00', '17:00'), (2, '08:00', '17:00'), (3, '08:00', '17:00'),
    (4, '08:00', '17:00'), (5, '08:00', '17:00'), (6, '08:00', '17:00'),
    (7, NULL, NULL);

-- Days the library is closed although it would normally be open: public and Poya holidays.
-- Not seeded: Poya days follow the moon, so the admin enters each year's real dates.
CREATE TABLE holidays (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    date       DATE         NOT NULL,
    name_en    VARCHAR(120) NOT NULL,
    name_si    VARCHAR(120) NOT NULL,
    name_ta    VARCHAR(120) NOT NULL,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_holidays_date UNIQUE (date)
);
