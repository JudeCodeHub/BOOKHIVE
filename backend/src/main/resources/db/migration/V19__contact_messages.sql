-- Messages from the visit page's contact form (checklist step 207). Kept so staff can see and
-- answer them (a staff inbox comes later); each one is also emailed to the library's address.

CREATE TABLE contact_messages (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150)  NOT NULL,
    email       VARCHAR(254)  NOT NULL,
    subject     VARCHAR(200)  NOT NULL,
    message     VARCHAR(5000) NOT NULL,
    -- The page language, so staff can reply in it
    language    VARCHAR(2)    NOT NULL,
    -- Set when a staff member has dealt with it
    handled_at  TIMESTAMPTZ,
    handled_by  UUID          REFERENCES users (id),

    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_contact_messages_language CHECK (language IN ('en', 'si', 'ta'))
);

CREATE INDEX ix_contact_messages_open ON contact_messages (created_at) WHERE handled_at IS NULL;
