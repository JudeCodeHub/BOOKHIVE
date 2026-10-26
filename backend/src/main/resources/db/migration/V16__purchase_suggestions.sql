-- Members suggest books for the library to buy (CAT-11, checklist step 170). Staff mark each
-- suggestion ordered, acquired (linked to the new book) or declined, with a note the member sees.

CREATE TABLE purchase_suggestions (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Who suggested it (their account; suggestions need a login)
    user_id      UUID          NOT NULL REFERENCES users (id),
    title        VARCHAR(300)  NOT NULL,
    author       VARCHAR(200),
    -- Digits only, as in books.isbn13 (normalised from ISBN-10 if needed)
    isbn13       VARCHAR(13),
    -- Why the member wants it ("for my A/L studies")
    reason       VARCHAR(1000),
    status       VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
    -- Shown to the member, e.g. why it was declined or when it should arrive
    staff_note   VARCHAR(1000),
    -- Set when the library has bought it, so the member can go straight to the book
    book_id      UUID          REFERENCES books (id),
    decided_by   UUID          REFERENCES users (id),
    decided_at   TIMESTAMPTZ,

    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_suggestions_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_suggestions_isbn CHECK (isbn13 ~ '^97[89][0-9]{10}$'),
    CONSTRAINT ck_suggestions_status
        CHECK (status IN ('SUBMITTED', 'ORDERED', 'ACQUIRED', 'DECLINED')),
    CONSTRAINT ck_suggestions_acquired_book CHECK (status <> 'ACQUIRED' OR book_id IS NOT NULL)
);

CREATE INDEX ix_suggestions_user ON purchase_suggestions (user_id, created_at DESC);
CREATE INDEX ix_suggestions_status ON purchase_suggestions (status, created_at);
-- "How many people asked for this ISBN?" (popular requests first)
CREATE INDEX ix_suggestions_isbn ON purchase_suggestions (isbn13) WHERE isbn13 IS NOT NULL;
