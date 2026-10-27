-- Per-member channel choices (NOTIF-5, checklist step 313): one row per member and optional
-- notification type, only once they change something; no row = the type's defaults. Security
-- messages and receipts can't be switched off, so they never get a row.

CREATE TABLE notification_preferences (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    email      BOOLEAN     NOT NULL,
    sms        BOOLEAN     NOT NULL,
    in_app     BOOLEAN     NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_notification_preferences UNIQUE (user_id, type)
);
