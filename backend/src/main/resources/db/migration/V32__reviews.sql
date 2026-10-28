-- Book reviews and ratings (PRD COMM-1, checklist step 337). Only members who borrowed the book can
-- write one; staff moderate it before it appears on the website. One review per member per book:
-- editing it sends it back to moderation.

CREATE TABLE reviews (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id       UUID          NOT NULL REFERENCES books (id),
    member_id     UUID          NOT NULL REFERENCES members (id),
    rating        SMALLINT      NOT NULL,
    -- Plain text (no HTML): shown as text on the website
    text          VARCHAR(2000) NOT NULL DEFAULT '',
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500),
    moderated_by  UUID          REFERENCES users (id),
    moderated_at  TIMESTAMPTZ,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_reviews_member_book UNIQUE (member_id, book_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_reviews_reject_reason CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);

-- A book's approved reviews, newest first (and its average rating)
CREATE INDEX ix_reviews_book_approved ON reviews (book_id, created_at DESC) WHERE status = 'APPROVED';
-- The moderation queue, oldest first
CREATE INDEX ix_reviews_pending ON reviews (updated_at) WHERE status = 'PENDING';
