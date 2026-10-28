-- Wishlist (checklist step 338): books a member wants to read later. Saving a book twice is the
-- same as once.

CREATE TABLE wishlist_items (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id  UUID        NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    book_id    UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_wishlist_member_book UNIQUE (member_id, book_id)
);

-- A member's list, newest first
CREATE INDEX ix_wishlist_member ON wishlist_items (member_id, added_at DESC);
