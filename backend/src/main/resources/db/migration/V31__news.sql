-- Library news and announcements (PRD EVT-4, checklist step 335): new opening hours, a closure for
-- a holiday, new books. Staff write a DRAFT and publish it; an important one can be pinned on top.

CREATE TABLE news (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    -- One or two sentences for lists and the home page
    summary      VARCHAR(500) NOT NULL DEFAULT '',
    -- Rich text from the staff editor, sanitised on the server before it's saved (step 336)
    body_html    TEXT         NOT NULL DEFAULT '',
    image_key    VARCHAR(300),
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- Shown above the others while it matters (e.g. "Closed for Vesak")
    pinned       BOOLEAN      NOT NULL DEFAULT false,
    published_at TIMESTAMPTZ,
    author_id    UUID         REFERENCES users (id),

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_news_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_news_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

-- The public list: pinned first, then newest
CREATE INDEX ix_news_published ON news (pinned DESC, published_at DESC) WHERE status = 'PUBLISHED';
