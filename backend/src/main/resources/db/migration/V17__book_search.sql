-- Book search (checklist steps 188-190).
--
-- V11 made search_vector a generated column, which can only use the book's own row. Search must
-- also find books by author and by subject, which live in other tables. So both search columns
-- are now ordinary columns kept up to date by triggers: whenever a book, its authors or
-- categories, or an author's or category's name changes, the affected books are recalculated.
--
--   search_vector  full-text, ranked:  title/authors/ISBN (A) > subtitle (B) > categories (C)
--                                      > description (D)
--   search_text    plain lower-case "title subtitle authors", for typo-tolerant trigram
--                  matching ("harry poter", "wickramasinge")
--
-- Text config 'simple' (step 190): PostgreSQL has no Sinhala or Tamil stemmer, and English
-- stemming would treat the three languages differently. 'simple' lowercases and splits words the
-- same way for all of them; typos and word forms are covered by the trigram side instead.

ALTER TABLE books DROP COLUMN search_vector;
ALTER TABLE books ADD COLUMN search_vector TSVECTOR;
ALTER TABLE books ADD COLUMN search_text TEXT;

-- The authors of a book in cover order, as one string
CREATE FUNCTION book_author_names(p_book_id UUID) RETURNS TEXT
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(string_agg(a.name, ' ' ORDER BY ba.position), '')
    FROM book_authors ba JOIN authors a ON a.id = ba.author_id
    WHERE ba.book_id = p_book_id
$$;

-- A book's category names in all three languages, plus their DDC numbers
CREATE FUNCTION book_category_text(p_book_id UUID) RETURNS TEXT
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(string_agg(
               c.name_en || ' ' || c.name_si || ' ' || c.name_ta || ' ' || coalesce(c.ddc_code, ''),
               ' '), '')
    FROM book_categories bc JOIN categories c ON c.id = bc.category_id
    WHERE bc.book_id = p_book_id
$$;

-- Fills both search columns of the row being written
CREATE FUNCTION books_search_fill() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    authors TEXT := book_author_names(NEW.id);
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A')
        || setweight(to_tsvector('simple', authors), 'A')
        || setweight(to_tsvector('simple',
               coalesce(NEW.isbn13, '') || ' ' || coalesce(NEW.isbn10, '')), 'A')
        || setweight(to_tsvector('simple', coalesce(NEW.subtitle, '')), 'B')
        || setweight(to_tsvector('simple', book_category_text(NEW.id)), 'C')
        || setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'D');
    NEW.search_text :=
        lower(concat_ws(' ', NEW.title, NEW.subtitle, nullif(authors, '')));
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_books_search
    BEFORE INSERT OR UPDATE OF title, subtitle, isbn10, isbn13, description, search_vector
    ON books
    FOR EACH ROW EXECUTE FUNCTION books_search_fill();

-- Something a book's search columns depend on changed: touch the book so the trigger above
-- recalculates them. (Setting search_vector to NULL is just the "touch".)
CREATE FUNCTION books_search_touch(p_book_ids UUID[]) RETURNS void
    LANGUAGE sql AS $$
    UPDATE books SET search_vector = NULL WHERE id = ANY (p_book_ids)
$$;

-- Authors or categories added to / removed from a book
CREATE FUNCTION book_links_search_refresh() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM books_search_touch(ARRAY[NEW.book_id]);
    END IF;
    IF TG_OP IN ('DELETE', 'UPDATE') THEN
        PERFORM books_search_touch(ARRAY[OLD.book_id]);
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_book_authors_search
    AFTER INSERT OR UPDATE OR DELETE ON book_authors
    FOR EACH ROW EXECUTE FUNCTION book_links_search_refresh();

CREATE TRIGGER trg_book_categories_search
    AFTER INSERT OR UPDATE OR DELETE ON book_categories
    FOR EACH ROW EXECUTE FUNCTION book_links_search_refresh();

-- An author renamed: all their books
CREATE FUNCTION authors_search_refresh() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM books_search_touch(
        ARRAY(SELECT book_id FROM book_authors WHERE author_id = NEW.id));
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_authors_search
    AFTER UPDATE OF name ON authors
    FOR EACH ROW EXECUTE FUNCTION authors_search_refresh();

-- A category renamed: all its books
CREATE FUNCTION categories_search_refresh() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM books_search_touch(
        ARRAY(SELECT book_id FROM book_categories WHERE category_id = NEW.id));
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_categories_search
    AFTER UPDATE OF name_en, name_si, name_ta, ddc_code ON categories
    FOR EACH ROW EXECUTE FUNCTION categories_search_refresh();

-- Fill both columns for the books already in the catalog
UPDATE books SET search_vector = NULL;

-- Full-text search (step 188)
CREATE INDEX ix_books_search ON books USING gin (search_vector);
-- Typo-tolerant matching on title + subtitle + authors (step 189): similarity() and the % and
-- <% operators use this index ("harry poter" still finds "Harry Potter")
CREATE INDEX ix_books_search_text_trgm ON books USING gin (search_text gin_trgm_ops);
