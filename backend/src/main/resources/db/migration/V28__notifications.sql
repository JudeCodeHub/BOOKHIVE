-- In-app notifications (NOTIF-3, checklist step 312): what the bell shows. The words aren't
-- stored: only the type and its values (book title, date...), so each message is written in
-- whatever language the member is using the site in when they read it.

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    -- The values the message needs, as text: {"title": "...", "date": "2026-11-09", ...}
    params     JSONB        NOT NULL DEFAULT '{}',
    -- Where it leads in the site, without the language: /account/holds
    link       VARCHAR(300),
    -- The same reminder is shown only once per member
    dedupe_key VARCHAR(200),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The bell list, newest first
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
-- The unread count
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
CREATE UNIQUE INDEX uk_notifications_dedupe ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;
