-- Titles (checklist steps 149-151, ADR 0003). A book is the title-level record; the physical
-- items are copies (step 152). Authors and categories are linked through join tables.

CREATE TABLE books (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    title        VARCHAR(300) NOT NULL,
    subtitle     VARCHAR(300),
    -- Digits only (ISBN-10 may end in X). Stored without hyphens so "978-955-..." matches.
    isbn10       VARCHAR(10),
    isbn13       VARCHAR(13),
    edition      VARCHAR(50),
    publish_year SMALLINT,
    pages        INTEGER,
    -- ISO 639-1 code of the book's text: si, ta, en (others allowed, e.g. fr)
    language     VARCHAR(10)  NOT NULL,
    description  TEXT,
    -- CAT-12: what kind of item it is
    format       VARCHAR(20)  NOT NULL DEFAULT 'BOOK',
    -- Object storage key of the cover image (step on covers); NULL = no cover yet
    cover_key    VARCHAR(300),
    -- Shelf label, e.g. '891.483 WIC' (DDC number + first letters of the author)
    call_number  VARCHAR(50),
    publisher_id UUID         REFERENCES publishers (id),

    -- Full-text search over the book's own fields, kept up to date by PostgreSQL itself.
    -- 'simple' = no stemming: there's no Sinhala or Tamil stemmer, and it treats all three
    -- languages the same. Weights: title A, subtitle B, ISBNs A, description D.
    -- Authors and categories are added in the search phase (they live in other tables).
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A')
        || setweight(to_tsvector('simple', coalesce(subtitle, '')), 'B')
        || setweight(to_tsvector('simple', coalesce(isbn13, '') || ' ' || coalesce(isbn10, '')), 'A')
        || setweight(to_tsvector('simple', coalesce(description, '')), 'D')
    ) STORED,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_books_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_books_isbn10 CHECK (isbn10 ~ '^[0-9]{9}[0-9X]$'),
    CONSTRAINT ck_books_isbn13 CHECK (isbn13 ~ '^97[89][0-9]{10}$'),
    CONSTRAINT ck_books_year CHECK (publish_year BETWEEN 1000 AND 2100),
    CONSTRAINT ck_books_pages CHECK (pages > 0),
    CONSTRAINT ck_books_language CHECK (language ~ '^[a-z]{2,3}$'),
    CONSTRAINT ck_books_format
        CHECK (format IN ('BOOK', 'MAGAZINE', 'NEWSPAPER', 'DVD', 'AUDIOBOOK'))
);

-- One title per ISBN: scanning an ISBN while cataloguing finds the existing book instead of
-- creating a duplicate. (Books without an ISBN, common for older local books, are allowed.)
CREATE UNIQUE INDEX uk_books_isbn13 ON books (isbn13) WHERE isbn13 IS NOT NULL;
CREATE UNIQUE INDEX uk_books_isbn10 ON books (isbn10) WHERE isbn10 IS NOT NULL;
CREATE INDEX ix_books_search ON books USING gin (search_vector);
-- Typo-tolerant title search ("gamperaliy") and "starts with" browsing
CREATE INDEX ix_books_title_trgm ON books USING gin (title gin_trgm_ops);
CREATE INDEX ix_books_publisher ON books (publisher_id);
CREATE INDEX ix_books_language ON books (language);

-- Who made the book, in the order printed on the cover (step 150).
-- One person can have two roles on the same book (author and illustrator).
CREATE TABLE book_authors (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id    UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES authors (id),
    role       VARCHAR(20) NOT NULL DEFAULT 'AUTHOR',
    -- 0 = first name shown
    position   SMALLINT    NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_book_authors UNIQUE (book_id, author_id, role),
    CONSTRAINT ck_book_authors_role
        CHECK (role IN ('AUTHOR', 'EDITOR', 'TRANSLATOR', 'ILLUSTRATOR')),
    CONSTRAINT ck_book_authors_position CHECK (position >= 0)
);

-- "All books by this author" (author pages, search)
CREATE INDEX ix_book_authors_author ON book_authors (author_id);

-- Which categories a book is in (step 151). A pure link table: no extra data, so no id.
CREATE TABLE book_categories (
    book_id     UUID NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id),

    CONSTRAINT pk_book_categories PRIMARY KEY (book_id, category_id)
);

-- "All books in this category" (browse, filters)
CREATE INDEX ix_book_categories_category ON book_categories (category_id);
