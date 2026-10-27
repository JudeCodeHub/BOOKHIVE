-- Outbox (ADR 0004, NOTIF-6, checklist step 307): every email and SMS is saved here first, then
-- sent by the dispatcher job with retries (step 308). Nothing is lost if the mail server or the
-- SMS gateway is down, or the app restarts.
--
--   PENDING  waiting to be sent (first try, or a retry at next_attempt_at)
--   SENT     the provider accepted it
--   FAILED   gave up after the maximum attempts; shown to admins (step 319)

CREATE TABLE outbox_messages (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    channel           VARCHAR(10)  NOT NULL,
    -- Which kind of message (NotificationType), for the admin view and statistics
    notification_type VARCHAR(40)  NOT NULL,
    -- The account it's for; NULL for messages to the library itself (contact form)
    user_id           UUID         REFERENCES users (id),
    -- Email address or phone number, as it was when queued
    recipient         VARCHAR(254) NOT NULL,
    language          VARCHAR(2)   NOT NULL DEFAULT 'en',
    -- Email subject; NULL for SMS
    subject           VARCHAR(300),
    body_text         TEXT         NOT NULL,
    -- Email HTML version; NULL for SMS and plain-text-only emails
    body_html         TEXT,
    -- The same reminder is only ever queued once (e.g. DUE_SOON:<loan>:<date>)
    dedupe_key        VARCHAR(200),

    status            VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error        VARCHAR(1000),
    sent_at           TIMESTAMPTZ,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_outbox_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbox_language CHECK (language IN ('en', 'si', 'ta')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_email_subject CHECK (channel <> 'EMAIL' OR subject IS NOT NULL),
    CONSTRAINT ck_outbox_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

-- One message per dedupe key and channel
CREATE UNIQUE INDEX uk_outbox_dedupe ON outbox_messages (dedupe_key, channel)
    WHERE dedupe_key IS NOT NULL;
-- The dispatcher: what's due to be sent now, oldest first
CREATE INDEX ix_outbox_due ON outbox_messages (next_attempt_at) WHERE status = 'PENDING';
-- Admin view of failed messages (step 319)
CREATE INDEX ix_outbox_failed ON outbox_messages (updated_at DESC) WHERE status = 'FAILED';
