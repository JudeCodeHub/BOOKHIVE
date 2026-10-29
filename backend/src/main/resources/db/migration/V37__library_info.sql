-- The library's own details (ADM-6, checklist step 377): name, address, contact, map pin and an
-- "about" text in three languages. Until now these came from environment variables; admins can
-- now change them on the Library info page without a release.
--
-- One row only. On the first start the row is filled from the old bookhive.library.* settings
-- (LibraryInfoBootstrap), so nothing is lost.

CREATE TABLE library_info (
    -- Always 1: there is one library
    id           SMALLINT     PRIMARY KEY DEFAULT 1,
    name         VARCHAR(150) NOT NULL,
    address_line VARCHAR(200) NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    postal_code  VARCHAR(10),
    phone        VARCHAR(30)  NOT NULL,
    email        VARCHAR(254) NOT NULL,
    -- Map pin
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    -- Shown on the visit page; sanitised HTML (RichText), may be empty
    about_en     TEXT         NOT NULL DEFAULT '',
    about_si     TEXT         NOT NULL DEFAULT '',
    about_ta     TEXT         NOT NULL DEFAULT '',

    updated_by   UUID         REFERENCES users (id),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_library_info_single_row CHECK (id = 1),
    CONSTRAINT ck_library_info_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_library_info_longitude CHECK (longitude BETWEEN -180 AND 180)
);
