-- Library events (PRD EVT-1 to EVT-3, checklist step 330): story time, book clubs, workshops.
-- Staff create them as DRAFT, publish them, and can cancel them (members are told why).

CREATE TABLE events (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title                 VARCHAR(200) NOT NULL,
    -- Rich text (staff editor), sanitised on the server before it's saved (step 336)
    description           TEXT         NOT NULL DEFAULT '',
    starts_at             TIMESTAMPTZ  NOT NULL,
    ends_at               TIMESTAMPTZ  NOT NULL,
    venue                 VARCHAR(200) NOT NULL,
    -- Who it's for: ALL, CHILDREN, YOUNG_ADULT, ADULT, SENIOR
    audience              VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    -- Members must sign up (then capacity and the waitlist apply); false = just come along
    registration_required BOOLEAN      NOT NULL DEFAULT true,
    -- Places; NULL = no limit
    capacity              INTEGER,
    -- Poster image in object storage (like book covers)
    image_key             VARCHAR(300),
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    cancel_reason         VARCHAR(500),
    created_by            UUID         REFERENCES users (id),
    published_at          TIMESTAMPTZ,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_events_times CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_events_audience
        CHECK (audience IN ('ALL', 'CHILDREN', 'YOUNG_ADULT', 'ADULT', 'SENIOR')),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT ck_events_cancel_reason CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
);

-- Public list and calendar: published events by date
CREATE INDEX ix_events_published_start ON events (starts_at) WHERE status = 'PUBLISHED';

-- One member's place at one event.
--   REGISTERED  has a place          WAITLISTED  event full, in line (first come, first served)
--   CANCELLED   gave the place up    ATTENDED    checked in at the door (QR, step 334)
CREATE TABLE event_registrations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events (id),
    member_id       UUID        NOT NULL REFERENCES members (id),
    status          VARCHAR(20) NOT NULL,
    -- When they joined (orders the waitlist) and when they got a place
    registered_at   TIMESTAMPTZ NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    checked_in_at   TIMESTAMPTZ,
    checked_in_by   UUID        REFERENCES users (id),
    -- Random code in the member's QR ticket; unguessable, so a ticket can't be made up
    check_in_code   VARCHAR(40) NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_reg_status
        CHECK (status IN ('REGISTERED', 'WAITLISTED', 'CANCELLED', 'ATTENDED')),
    CONSTRAINT ck_event_reg_confirmed
        CHECK (status NOT IN ('REGISTERED', 'ATTENDED') OR confirmed_at IS NOT NULL),
    CONSTRAINT ck_event_reg_attended
        CHECK ((status = 'ATTENDED') = (checked_in_at IS NOT NULL)),
    CONSTRAINT uk_event_reg_code UNIQUE (check_in_code)
);

-- A member signs up for an event once (a cancelled place doesn't count: they may sign up again)
CREATE UNIQUE INDEX uk_event_reg_member ON event_registrations (event_id, member_id)
    WHERE status <> 'CANCELLED';
-- Places taken and the waitlist order
CREATE INDEX ix_event_reg_event_status ON event_registrations (event_id, status, registered_at);
-- "My events"
CREATE INDEX ix_event_reg_member ON event_registrations (member_id, registered_at DESC);
