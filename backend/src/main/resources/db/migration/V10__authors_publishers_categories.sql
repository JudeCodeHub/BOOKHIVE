-- Catalog building blocks (checklist step 148, ADR 0003): authors, publishers and the category
-- tree. Books (step 149) link to these instead of repeating names as text.

CREATE TABLE authors (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- As shown to readers, e.g. "Martin Wickramasinghe" or "මාටින් වික්‍රමසිංහ"
    name       VARCHAR(200) NOT NULL,
    bio        TEXT,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_authors_name CHECK (btrim(name) <> '')
);

-- Two different people can share a name, so no unique constraint. Trigram index: staff typing
-- "wickramasinge" while cataloguing still find "Wickramasinghe" (and avoid duplicates).
CREATE INDEX ix_authors_name_trgm ON authors USING gin (name gin_trgm_ops);

CREATE TABLE publishers (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- citext: "Sarasavi Publishers" and "sarasavi publishers" are the same publisher
    name       CITEXT       NOT NULL,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_publishers_name UNIQUE (name),
    CONSTRAINT ck_publishers_name CHECK (btrim(name) <> '')
);

-- A tree based on the Dewey Decimal Classification: 800 Literature > 891.48 Sinhala literature.
-- Shown in menus and filters in all three languages.
CREATE TABLE categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID         REFERENCES categories (id),
    name_en    VARCHAR(120) NOT NULL,
    name_si    VARCHAR(120) NOT NULL,
    name_ta    VARCHAR(120) NOT NULL,
    -- DDC number, e.g. '891.48'. Optional: some library groupings ("New arrivals") have none.
    ddc_code   VARCHAR(20),
    -- Which shelf area the books belong to. Junior members may only borrow from CHILDREN and
    -- YOUNG_ADULT (membership_types.allowed_sections). The PRD's "YA" is spelled out here.
    section    VARCHAR(20)  NOT NULL DEFAULT 'ADULT',

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_categories_section
        CHECK (section IN ('ADULT', 'CHILDREN', 'YOUNG_ADULT', 'REFERENCE')),
    CONSTRAINT ck_categories_ddc CHECK (ddc_code ~ '^[0-9]{3}(\.[0-9]+)?$'),
    -- A category can't be its own parent; longer loops are prevented in CategoryService
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_id <> id)
);

-- No two siblings with the same name ("Fiction" twice under Literature)
CREATE UNIQUE INDEX uk_categories_parent_name
    ON categories (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name_en));
CREATE INDEX ix_categories_parent ON categories (parent_id);
CREATE INDEX ix_categories_ddc ON categories (ddc_code) WHERE ddc_code IS NOT NULL;
